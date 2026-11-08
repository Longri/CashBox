package com.cashbox.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Formulareingaben für ein Sparziel. Pflicht sind Name und Zielbetrag,
 * alles andere darf {@code null} sein. Geprüft und bereinigt wird im {@link SavingsGoalService}.
 */
public record GoalInput(String name, BigDecimal targetAmount, LocalDate targetDate, Integer priority,
                        String description) {
}
