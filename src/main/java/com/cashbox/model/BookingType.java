package com.cashbox.model;

/**
 * Art einer Buchung (Spalte {@code booking.type}).
 */
public enum BookingType {
    /** Geld kommt von außen auf das Sparkonto und landet im Puffer. */
    DEPOSIT,
    /** Geld wird zwischen Puffer und Sparzielen verschoben. */
    TRANSFER,
    /** Geld verlässt das Sparkonto (Sparziel wird ausgegeben). */
    WITHDRAWAL
}
