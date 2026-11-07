package com.cashbox.ui;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formatieren und Parsen von Geldbeträgen für die Oberfläche (deutsches Format).
 */
public final class Money {

    private static final Locale LOCALE = Locale.GERMANY;
    /** Ziffern mit höchstens einem Dezimaltrenner; Komma und Punkt sind gleichwertig. */
    private static final Pattern INPUT = Pattern.compile("\\d*([.,]\\d*)?");

    private Money() {
    }

    /** {@code 1234.5} → {@code "1.234,50 €"} */
    public static String format(BigDecimal value) {
        return String.format(LOCALE, "%,.2f €", value);
    }

    /** {@code 1234.5} → {@code "1234,50"} – zum Vorbelegen eines Eingabefelds. */
    public static String formatPlain(BigDecimal value) {
        return String.format(LOCALE, "%.2f", value);
    }

    /**
     * Liest eine Benutzereingabe wie {@code "12,5"}, {@code "12.50"} oder {@code ",5"}.
     * Tausendertrenner sind nicht erlaubt; die Nachkommastellen bleiben erhalten
     * (Skala der Eingabe), damit z. B. {@code "1.000"} als Fehler erkannt werden kann.
     *
     * @return den Betrag oder {@code null}, wenn die Eingabe kein Betrag ist
     */
    public static BigDecimal parse(String input) {
        if (input == null) {
            return null;
        }
        String text = input.strip();
        if (text.isEmpty() || !INPUT.matcher(text).matches() || text.matches("[.,]")) {
            return null;
        }
        return new BigDecimal(text.replace(',', '.'));
    }
}
