package com.cashbox.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Formulareingaben für ein Sparziel. Pflicht sind Name und Zielbetrag,
 * alles andere darf {@code null} sein. Geprüft und bereinigt wird im {@link SavingsGoalService}.
 *
 * @param icon Symbol bzw. eigenes Bild; {@code null} wie {@link GoalIconChoice#UNCHANGED}
 */
public record GoalInput(String name, BigDecimal targetAmount, LocalDate targetDate, Integer priority,
                        String description, GoalIconChoice icon) {

    public GoalInput {
        if (icon == null) {
            icon = GoalIconChoice.UNCHANGED;
        }
    }

    /** Ohne Angabe zu Symbol und Bild – beides bleibt unverändert. */
    public GoalInput(String name, BigDecimal targetAmount, LocalDate targetDate, Integer priority,
                     String description) {
        this(name, targetAmount, targetDate, priority, description, GoalIconChoice.UNCHANGED);
    }
}
