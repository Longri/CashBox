package com.cashbox.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.Account;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;
import com.cashbox.model.SavingsGoalImage;
import com.cashbox.repository.AccountRepository;
import com.cashbox.repository.BookingRepository;
import com.cashbox.repository.SavingsGoalImageRepository;
import com.cashbox.repository.SavingsGoalRepository;

/**
 * Anlegen, Bearbeiten und Löschen von Sparzielen. Geld wird hier nie bewegt –
 * das geschieht ausschließlich über den {@link BookingService}, damit jede Bewegung
 * in der Buchungshistorie steht.
 * <p>
 * Löschen ist nur für leere Ziele ohne Buchungen erlaubt (die Fremdschlüssel in
 * {@code booking} sind {@code ON DELETE RESTRICT}); alle anderen Ziele werden
 * pausiert oder als erreicht markiert.
 * <p>
 * Jedes Ziel zeigt ein Standardsymbol ({@link com.cashbox.model.GoalIcon}) oder ein eigenes Bild
 * ({@link GoalImage}, ein Ausschnitt aus dem hochgeladenen {@link GoalPhoto}); was sich ändern soll,
 * steht in {@link GoalInput#icon()}.
 */
@Service
public class SavingsGoalService {

    public static final int MAX_NAME_LENGTH = 100;
    /** Die Spalte ist TEXT (64 KB); die Grenze hält Eingaben überschaubar. */
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 999;

    private final AccountRepository accounts;
    private final SavingsGoalRepository goals;
    private final BookingRepository bookings;
    private final SavingsGoalImageRepository images;

    public SavingsGoalService(AccountRepository accounts, SavingsGoalRepository goals, BookingRepository bookings,
                              SavingsGoalImageRepository images) {
        this.accounts = accounts;
        this.goals = goals;
        this.bookings = bookings;
        this.images = images;
    }

    /** Alle Sparziele des Kontos in Anzeigereihenfolge (aktiv zuerst, dann Priorität, dann Name). */
    @Transactional(readOnly = true)
    public List<GoalDetails> findAll() {
        Account account = loadAccount();
        Set<Long> withImage = images.findGoalIdsWithImage(account.getId());
        return goals.findByAccountId(account.getId()).stream()
                .sorted(SavingsGoal.DISPLAY_ORDER)
                .map(goal -> GoalDetails.of(goal, withImage.contains(goal.getId())))
                .toList();
    }

    /** Das angezeigte Bild (Ausschnitt) eines Sparziels, falls es eines hat. */
    @Transactional(readOnly = true)
    public Optional<GoalImage> findImage(Long goalId) {
        return images.findThumbnail(Objects.requireNonNull(goalId, "goalId"))
                .map(image -> new GoalImage(image.getData(), image.getContentType()));
    }

    /** Quellbild und Ausschnitt eines Sparziels – zum nachträglichen Ändern des Ausschnitts. */
    @Transactional(readOnly = true)
    public Optional<StoredPhoto> findPhoto(Long goalId) {
        return images.findById(Objects.requireNonNull(goalId, "goalId"))
                .map(image -> new StoredPhoto(new GoalPhoto(image.getSourceData(), image.getSourceContentType()),
                        new ImageCrop(image.getCropX(), image.getCropY(), image.getCropSize())));
    }

    /** Legt ein neues, aktives und leeres Sparziel an. */
    @Transactional
    public GoalDetails create(GoalInput input) {
        Objects.requireNonNull(input, "input");
        Account account = loadAccount();
        String name = normalizeName(input.name());
        if (goals.existsByAccountIdAndNameIgnoreCase(account.getId(), name)) {
            throw duplicateName(name);
        }
        SavingsGoal goal = new SavingsGoal(account, name, normalizeTarget(input.targetAmount()));
        applyOptionalFields(goal, input);
        applySymbol(goal, input.icon());
        SavingsGoal saved = saveAndFlush(goal);
        return GoalDetails.of(saved, applyImage(saved.getId(), input.icon()));
    }

    /**
     * Ändert Stammdaten, Symbol bzw. Bild und Status eines Sparziels. Der zugewiesene Betrag bleibt unverändert.
     *
     * @param version die Version, mit der das Formular geöffnet wurde
     * @throws org.springframework.dao.OptimisticLockingFailureException wenn das Ziel inzwischen geändert wurde
     */
    @Transactional
    public GoalDetails update(Long id, Long version, GoalInput input, GoalStatus status) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(status, "status");
        Account account = loadAccount();
        SavingsGoal goal = loadGoal(account, id, version);
        String name = normalizeName(input.name());
        if (goals.existsByAccountIdAndNameIgnoreCaseAndIdNot(account.getId(), name, id)) {
            throw duplicateName(name);
        }
        goal.setName(name);
        goal.setTargetAmount(normalizeTarget(input.targetAmount()));
        applyOptionalFields(goal, input);
        applySymbol(goal, input.icon());
        goal.setStatus(status);
        SavingsGoal saved = saveAndFlush(goal);
        return GoalDetails.of(saved, applyImage(saved.getId(), input.icon()));
    }

    /**
     * Schiebt ein Sparziel innerhalb seines Status an eine neue Position (Ziehen in der Liste)
     * und nummeriert die Priorität aller Ziele dieses Status neu: 1 = ganz oben.
     * Ziele ohne Priorität bekommen dabei ebenfalls eine, damit Position und Priorität übereinstimmen.
     *
     * @param newIndex Zielposition in der Liste des Status (0 = ganz oben); wird auf die Listengrenzen begrenzt
     * @throws org.springframework.dao.OptimisticLockingFailureException wenn ein Ziel gleichzeitig geändert wurde
     */
    @Transactional
    public void move(Long id, int newIndex) {
        Account account = loadAccount();
        SavingsGoal goal = loadGoal(account, id, null);
        List<SavingsGoal> group = new ArrayList<>(goals.findByAccountId(account.getId()).stream()
                .filter(g -> g.getStatus() == goal.getStatus() && !g.getId().equals(goal.getId()))
                .sorted(SavingsGoal.DISPLAY_ORDER)
                .toList());
        if (group.size() + 1 > MAX_PRIORITY) {
            throw new IllegalStateException("Mehr als " + MAX_PRIORITY + " Sparziele lassen sich nicht sortieren.");
        }
        group.add(Math.clamp(newIndex, 0, group.size()), goal);
        for (int i = 0; i < group.size(); i++) {
            group.get(i).setPriority(MIN_PRIORITY + i);
        }
        // Sofort schreiben, damit Konflikte (Optimistic Locking) hier und nicht erst beim Commit auffallen.
        goals.flush();
    }

    /** {@code true}, wenn das Ziel leer ist und keine Buchungen hat. */
    @Transactional(readOnly = true)
    public boolean canDelete(Long id) {
        return deleteBlocker(loadGoal(loadAccount(), id, null)) == null;
    }

    /**
     * Löscht ein leeres Sparziel ohne Buchungen.
     *
     * @throws IllegalStateException wenn das Ziel Geld oder Buchungen hat
     */
    @Transactional
    public void delete(Long id, Long version) {
        SavingsGoal goal = loadGoal(loadAccount(), id, version);
        String blocker = deleteBlocker(goal);
        if (blocker != null) {
            throw new IllegalStateException(blocker);
        }
        // Die Datenbank löscht das Bild ohnehin mit (ON DELETE CASCADE); hier zusätzlich, damit auch
        // der Persistenzkontext dieser Transaktion es nicht mehr kennt.
        images.deleteById(goal.getId());
        goals.delete(goal);
    }

    // ------------------------------------------------------------------ intern

    private String deleteBlocker(SavingsGoal goal) {
        if (goal.getAssignedAmount().signum() != 0) {
            return "„" + goal.getName() + "“ enthält noch Geld. Buche es zuerst um.";
        }
        if (bookings.existsForGoal(goal.getId())) {
            return "„" + goal.getName() + "“ hat bereits Buchungen und kann nicht gelöscht werden. "
                    + "Setze es stattdessen auf „" + GoalStatus.PAUSED.getLabel() + "“ oder „"
                    + GoalStatus.REACHED.getLabel() + "“.";
        }
        return null;
    }

    private SavingsGoal saveAndFlush(SavingsGoal goal) {
        try {
            // Sofort schreiben, damit Verstöße gegen den eindeutigen Namen hier ankommen
            // (z. B. wenn jemand gleichzeitig dasselbe Ziel anlegt) und die neue Version feststeht.
            return goals.saveAndFlush(goal);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName(goal.getName());
        }
    }

    private Account loadAccount() {
        return accounts.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Es ist kein Sparkonto angelegt."));
    }

    /** @param version erwartete Version oder {@code null}, wenn nicht geprüft werden soll */
    private SavingsGoal loadGoal(Account account, Long id, Long version) {
        SavingsGoal goal = goals.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Das Sparziel existiert nicht mehr."));
        if (!goal.getAccount().getId().equals(account.getId())) {
            throw new IllegalArgumentException("Das Sparziel gehört nicht zu diesem Konto.");
        }
        if (version != null && !version.equals(goal.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(SavingsGoal.class, id);
        }
        return goal;
    }

    /** Setzt das Symbol (auch {@code null} = Standard). Ein eigenes Bild verdrängt das Symbol nicht. */
    private static void applySymbol(SavingsGoal goal, GoalIconChoice choice) {
        if (choice instanceof GoalIconChoice.Symbol symbol) {
            goal.setIcon(symbol.icon());
        }
    }

    /**
     * Legt das eigene Bild an, ersetzt oder entfernt es bzw. ändert seinen Ausschnitt.
     *
     * @return ob das Ziel danach ein eigenes Bild hat
     */
    private boolean applyImage(Long goalId, GoalIconChoice choice) {
        return switch (choice) {
            case GoalIconChoice.Unchanged unchanged -> images.existsById(goalId);
            case GoalIconChoice.Symbol symbol -> {
                images.deleteById(goalId);
                yield false;
            }
            case GoalIconChoice.Image image -> {
                SavingsGoalImage stored = images.findById(goalId).orElseGet(() -> new SavingsGoalImage(goalId));
                stored.setSource(image.photo().contentType(), image.photo().rawData());
                setCrop(stored, image.photo(), image.crop());
                images.saveAndFlush(stored);
                yield true;
            }
            case GoalIconChoice.Crop crop -> {
                SavingsGoalImage stored = images.findById(goalId).orElseThrow(() -> new IllegalStateException(
                        "Das Sparziel hat kein eigenes Bild mehr. Bitte das Bild neu hochladen."));
                setCrop(stored, new GoalPhoto(stored.getSourceData(), stored.getSourceContentType()), crop.crop());
                images.flush();
                yield true;
            }
        };
    }

    private static void setCrop(SavingsGoalImage stored, GoalPhoto photo, ImageCrop crop) {
        GoalImage image = GoalImage.render(photo, crop);
        stored.setCrop(crop.centerX(), crop.centerY(), crop.size(), image.contentType(), image.rawData());
    }

    private static void applyOptionalFields(SavingsGoal goal, GoalInput input) {
        goal.setTargetDate(input.targetDate());
        goal.setPriority(normalizePriority(input.priority()));
        goal.setDescription(normalizeDescription(input.description()));
    }

    static String normalizeName(String name) {
        String text = name == null ? "" : name.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Bitte einen Namen eingeben.");
        }
        if (text.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Der Name darf höchstens " + MAX_NAME_LENGTH + " Zeichen haben.");
        }
        return text;
    }

    static BigDecimal normalizeTarget(BigDecimal amount) {
        return BookingService.normalizeAmount(amount, "Der Zielbetrag");
    }

    static Integer normalizePriority(Integer priority) {
        if (priority != null && (priority < MIN_PRIORITY || priority > MAX_PRIORITY)) {
            throw new IllegalArgumentException(
                    "Die Priorität muss zwischen " + MIN_PRIORITY + " und " + MAX_PRIORITY + " liegen.");
        }
        return priority;
    }

    static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String text = description.strip();
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Die Beschreibung darf höchstens " + MAX_DESCRIPTION_LENGTH + " Zeichen haben.");
        }
        return text;
    }

    private static IllegalArgumentException duplicateName(String name) {
        return new IllegalArgumentException("Es gibt bereits ein Sparziel mit dem Namen „" + name + "“.");
    }
}
