package com.cashbox.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.Account;
import com.cashbox.model.Booking;
import com.cashbox.model.BookingType;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;
import com.cashbox.repository.AccountRepository;
import com.cashbox.repository.BookingRepository;
import com.cashbox.repository.SavingsGoalImageRepository;
import com.cashbox.repository.SavingsGoalRepository;

/**
 * Ein- und Umbuchungen. Jede Buchung wird als unveränderlicher {@link Booking}-Eintrag
 * protokolliert; nach jeder Änderung wird die Konto-Invariante geprüft
 * (Kontostand = Puffer + Summe der Sparziele), sonst wird die Transaktion zurückgerollt.
 */
@Service
public class BookingService {

    /** Obergrenze pro Buchung, weit unter dem DB-Limit DECIMAL(19,2). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    public static final int MAX_NOTE_LENGTH = 255;

    private final AccountRepository accounts;
    private final SavingsGoalRepository goals;
    private final BookingRepository bookings;
    private final SavingsGoalImageRepository images;

    public BookingService(AccountRepository accounts, SavingsGoalRepository goals, BookingRepository bookings,
                          SavingsGoalImageRepository images) {
        this.accounts = accounts;
        this.goals = goals;
        this.bookings = bookings;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public AccountOverview getOverview() {
        Account account = loadAccount();
        Set<Long> withImage = images.findGoalIdsWithImage(account.getId());
        List<AccountOverview.GoalInfo> goalInfos = goals.findByAccountId(account.getId()).stream()
                .sorted(SavingsGoal.DISPLAY_ORDER)
                .map(g -> new AccountOverview.GoalInfo(g.getId(), g.getName(), g.getAssignedAmount(),
                        g.getTargetAmount(), g.getStatus(), g.getIcon(), withImage.contains(g.getId())))
                .toList();
        return new AccountOverview(account.getName(), account.getCurrentBalance(),
                account.getUnassignedBuffer(), goalInfos);
    }

    /**
     * Neues Geld auf das Sparkonto buchen. Es landet im Puffer; ist ein Sparziel
     * angegeben, wird es in derselben Transaktion direkt dorthin umgebucht.
     *
     * @param targetGoalId Sparziel oder {@code null} für den Puffer
     */
    @Transactional
    public void deposit(BigDecimal amount, Long targetGoalId, String note) {
        BigDecimal value = normalizeAmount(amount);
        String text = normalizeNote(note);
        Account account = loadAccount();
        // Ziel zuerst prüfen, damit bei einem ungültigen Ziel nichts verändert wird.
        SavingsGoal target = targetGoalId == null ? null : loadTargetGoal(account, targetGoalId);
        Instant now = Instant.now();

        account.deposit(value);
        bookings.save(new Booking(account, BookingType.DEPOSIT, value, null, null, text, now));

        if (target != null) {
            account.takeFromBuffer(value);
            target.assign(value);
            bookings.save(new Booking(account, BookingType.TRANSFER, value, null, target, text, now));
        }
        verifyConsistency(account);
    }

    /**
     * Geld zwischen Puffer und Sparzielen verschieben ({@code null} = Puffer).
     * Ziel muss der Puffer oder ein aktives Sparziel sein; als Quelle ist jedes Ziel erlaubt,
     * damit Restgeld aus pausierten oder erreichten Zielen zurückgebucht werden kann.
     */
    @Transactional
    public void transfer(Long sourceGoalId, Long targetGoalId, BigDecimal amount, String note) {
        if (sourceGoalId == null && targetGoalId == null || sourceGoalId != null && sourceGoalId.equals(targetGoalId)) {
            throw new IllegalArgumentException("Quelle und Ziel müssen sich unterscheiden.");
        }
        BigDecimal value = normalizeAmount(amount);
        String text = normalizeNote(note);
        Account account = loadAccount();

        SavingsGoal source = sourceGoalId == null ? null : loadGoal(account, sourceGoalId);
        SavingsGoal target = targetGoalId == null ? null : loadTargetGoal(account, targetGoalId);

        if (source == null) {
            account.takeFromBuffer(value);
        } else {
            source.release(value);
        }
        if (target == null) {
            account.addToBuffer(value);
        } else {
            target.assign(value);
        }
        bookings.save(new Booking(account, BookingType.TRANSFER, value, source, target, text, Instant.now()));
        verifyConsistency(account);
    }

    private Account loadAccount() {
        return accounts.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Es ist kein Sparkonto angelegt."));
    }

    private SavingsGoal loadGoal(Account account, Long goalId) {
        SavingsGoal goal = goals.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Das Sparziel existiert nicht mehr."));
        if (!goal.getAccount().getId().equals(account.getId())) {
            throw new IllegalArgumentException("Das Sparziel gehört nicht zu diesem Konto.");
        }
        return goal;
    }

    /** Wie {@link #loadGoal}, aber nur aktive Ziele dürfen neues Geld bekommen. */
    private SavingsGoal loadTargetGoal(Account account, Long goalId) {
        SavingsGoal goal = loadGoal(account, goalId);
        if (!goal.getStatus().acceptsMoney()) {
            throw new IllegalArgumentException("Das Sparziel „" + goal.getName() + "“ ist "
                    + goal.getStatus().getLabel().toLowerCase(Locale.GERMAN)
                    + " und nimmt kein Geld an. Setze es zuerst wieder auf „"
                    + GoalStatus.ACTIVE.getLabel() + "“.");
        }
        return goal;
    }

    static BigDecimal normalizeAmount(BigDecimal amount) {
        return normalizeAmount(amount, "Der Betrag");
    }

    /** @param subject Subjekt der Fehlermeldung, z. B. „Der Zielbetrag“ */
    static BigDecimal normalizeAmount(BigDecimal amount, String subject) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(subject + " muss größer als 0 sein.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(subject + " darf höchstens zwei Nachkommastellen haben.");
        }
        if (amount.compareTo(MAX_AMOUNT) >= 0) {
            throw new IllegalArgumentException(subject + " ist zu groß.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String text = note.strip();
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Die Notiz darf höchstens " + MAX_NOTE_LENGTH + " Zeichen haben.");
        }
        return text;
    }

    private void verifyConsistency(Account account) {
        BigDecimal assigned = goals.findByAccountId(account.getId()).stream()
                .map(SavingsGoal::getAssignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (account.getCurrentBalance().compareTo(account.getUnassignedBuffer().add(assigned)) != 0) {
            throw new IllegalStateException("Inkonsistenter Kontostand – Buchung wurde verworfen.");
        }
    }
}
