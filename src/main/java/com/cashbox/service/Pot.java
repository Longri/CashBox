package com.cashbox.service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ein „Topf", aus dem bzw. in den gebucht werden kann: der Puffer
 * ({@code goalId == null}) oder ein Sparziel. Gleichheit nur über die ID,
 * damit eine Auswahl auch nach dem Neuladen der Beträge erhalten bleibt.
 */
public record Pot(Long goalId, String name, BigDecimal available) {

    public static final String BUFFER_NAME = "Puffer";

    public static Pot buffer(BigDecimal available) {
        return new Pot(null, BUFFER_NAME, available);
    }

    public boolean isBuffer() {
        return goalId == null;
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
