package com.cashbox.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.Account;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;
import com.cashbox.repository.AccountRepository;
import com.cashbox.repository.BookingRepository;
import com.cashbox.repository.SavingsGoalRepository;

/**
 * Anlegen, Bearbeiten und Löschen von Sparzielen. Geld wird hier nie bewegt –
 * das geschieht ausschließlich über den {@link BookingService}, damit jede Bewegung
 * in der Buchungshistorie steht.
 * <p>
 * Löschen ist nur für leere Ziele ohne Buchungen erlaubt (die Fremdschlüssel in
 * {@code booking} sind {@code ON DELETE RESTRICT}); alle anderen Ziele werden
 * pausiert oder als erreicht markiert.
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

    public SavingsGoalService(AccountRepository accounts, SavingsGoalRepository goals, BookingRepository bookings) {
        this.accounts = accounts;
        this.goals = goals;
        this.bookings = bookings;
    }

    /** Alle Sparziele des Kontos in Anzeigereihenfolge (aktiv zuerst, dann Priorität, dann Name). */
    @Transactional(readOnly = true)
    public List<GoalDetails> findAll() {
        Account account = loadAccount();
        return goals.findByAccountId(account.getId()).stream()
                .sorted(SavingsGoal.DISPLAY_ORDER)
                .map(GoalDetails::of)
                .toList();
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
        return GoalDetails.of(saveAndFlush(goal));
    }

    /**
     * Ändert Stammdaten und Status eines Sparziels. Der zugewiesene Betrag bleibt unverändert.
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
        goal.setStatus(status);
        return GoalDetails.of(saveAndFlush(goal));
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
