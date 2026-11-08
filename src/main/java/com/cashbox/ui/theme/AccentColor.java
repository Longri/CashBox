package com.cashbox.ui.theme;

/**
 * Akzentfarbe für Buttons, Links und Markierungen. Die Farbwerte stehen je Theme in
 * theme-lumo.css bzw. theme-aura.css (Attribut {@code data-accent} am {@code <html>}).
 */
public enum AccentColor {

    /** Die Standardfarbe beider Themes. */
    BLUE("blue", "Blau"),
    PURPLE("purple", "Lila"),
    GREEN("green", "Grün"),
    ORANGE("orange", "Orange"),
    RED("red", "Rot");

    private final String key;
    private final String label;

    AccentColor(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Wert im Cookie und im Attribut {@code data-accent}. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    static AccentColor fromKey(String key, AccentColor fallback) {
        for (AccentColor color : values()) {
            if (color.key.equals(key)) {
                return color;
            }
        }
        return fallback;
    }
}
