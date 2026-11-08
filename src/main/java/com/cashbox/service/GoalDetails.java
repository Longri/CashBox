package com.cashbox.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cashbox.model.GoalIcon;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;

/**
 * Momentaufnahme eines Sparziels für die Sparziel-Verwaltung (keine JPA-Entität).
 * {@code version} wird beim Speichern zurückgegeben, damit zwischenzeitliche
 * Änderungen erkannt werden (Optimistic Locking).
 *
 * @param icon     gewähltes Standardsymbol oder {@code null} für {@link GoalIcon#DEFAULT}
 * @param hasImage {@code true}, wenn ein eigenes Bild hinterlegt ist – es wird dann statt des Symbols gezeigt
 */
public record GoalDetails(Long id, Long version, String name, String description, BigDecimal targetAmount,
                          BigDecimal assignedAmount, Integer priority, LocalDate targetDate, GoalStatus status,
                          GoalIcon icon, boolean hasImage) {

    static GoalDetails of(SavingsGoal goal, boolean hasImage) {
        return new GoalDetails(goal.getId(), goal.getVersion(), goal.getName(), goal.getDescription(),
                goal.getTargetAmount(), goal.getAssignedAmount(), goal.getPriority(), goal.getTargetDate(),
                goal.getStatus(), goal.getIcon(), hasImage);
    }

    /** Zielbetrag erreicht oder überschritten. */
    public boolean isFunded() {
        return assignedAmount.compareTo(targetAmount) >= 0;
    }
}
