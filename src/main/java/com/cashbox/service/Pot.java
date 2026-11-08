package com.cashbox.service;

import java.math.BigDecimal;
import java.util.Objects;

import com.cashbox.model.GoalStatus;

/**
 * Ein „Topf", aus dem bzw. in den gebucht werden kann: der Puffer
 * ({@code goalId == null}, {@code status == null}) oder ein Sparziel. Gleichheit nur über die ID,
 * damit eine Auswahl auch nach dem Neuladen der Beträge erhalten bleibt.
 */
public record Pot(Long goalId, String name, BigDecimal available, GoalStatus status) {

    public static final String BUFFER_NAME = "Puffer";

    public static Pot buffer(BigDecimal available) {
        return new Pot(null, BUFFER_NAME, available, null);
    }

    public boolean isBuffer() {
        return goalId == null;
    }

    /** Der Puffer und aktive Sparziele dürfen neues Geld bekommen. */
    public boolean acceptsMoney() {
        return status == null || status.acceptsMoney();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pot other && Objects.equals(goalId, other.goalId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(goalId);
    }
}
