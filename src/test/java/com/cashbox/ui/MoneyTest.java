package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesCommaAndDot() {
        assertEquals(new BigDecimal("12.5"), Money.parse("12,5"));
        assertEquals(new BigDecimal("12.50"), Money.parse(" 12.50 "));
        assertEquals(new BigDecimal("0.5"), Money.parse(",5"));
        assertEquals(new BigDecimal("12"), Money.parse("12,"));
    }

    @Test
    void keepsScaleSoThousandSeparatorsAreDetected() {
        assertEquals(3, Money.parse("1.000").scale());
    }

    @Test
    void rejectsNonAmounts() {
        assertNull(Money.parse(null));
        assertNull(Money.parse(""));
        assertNull(Money.parse(","));
        assertNull(Money.parse("1.000,50"));
        assertNull(Money.parse("12a"));
        assertNull(Money.parse("-5"));
    }

    @Test
    void formatsGerman() {
        assertEquals("1.234,50 €", Money.format(new BigDecimal("1234.5")));
        assertEquals("1234,50", Money.formatPlain(new BigDecimal("1234.5")));
    }
}
