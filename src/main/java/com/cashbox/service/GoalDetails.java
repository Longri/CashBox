package com.cashbox.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;

/**
 * Momentaufnahme eines Sparziels für die Sparziel-Verwaltung (keine JPA-Entität).
 * {@code version} wird beim Speichern zurückgegeben, damit zwischenzeitliche
 * Änderungen erkannt werden (Optimistic Locking).
 */
public record GoalDetails(Long id, Long version, String name, String description, BigDecimal targetAmount,
                          BigDecimal assignedAmount, Integer priority, LocalDate targetDate, GoalStatus status) {

    static GoalDetails of(SavingsGoal goal) {
        return new GoalDetails(goal.getId(), goal.getVersion(), goal.getName(), goal.getDescription(),
                goal.getTargetAmount(), goal.getAssignedAmount(), goal.getPriority(), goal.getTargetDate(),
                goal.getStatus());
    }

    /** Zielbetrag erreicht oder überschritten. */
    public boolean isFunded() {
        return assignedAmount.compareTo(targetAmount) >= 0;
    }
}
