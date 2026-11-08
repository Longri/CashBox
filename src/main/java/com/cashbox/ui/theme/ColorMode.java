package com.cashbox.ui.theme;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.page.ColorScheme;

/** Hell, dunkel oder wie im Betriebssystem eingestellt. Lumo und Aura unterstützen alle drei. */
public enum ColorMode {

    LIGHT("light", "Hell", VaadinIcon.SUN_O, ColorScheme.Value.LIGHT),
    DARK("dark", "Dunkel", VaadinIcon.MOON_O, ColorScheme.Value.DARK),
    /** {@code color-scheme: light dark}: der Browser folgt der Systemeinstellung, auch live beim Umschalten. */
    SYSTEM("system", "Automatisch", VaadinIcon.ADJUST, ColorScheme.Value.LIGHT_DARK);

    private final String key;
    private final String label;
    private final VaadinIcon icon;
    private final ColorScheme.Value colorScheme;

    ColorMode(String key, String label, VaadinIcon icon, ColorScheme.Value colorScheme) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.colorScheme = colorScheme;
    }

    /** Wert im Cookie. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public VaadinIcon icon() {
        return icon;
    }

    public ColorScheme.Value colorScheme() {
        return colorScheme;
    }

    static ColorMode fromKey(String key, ColorMode fallback) {
        for (ColorMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return fallback;
    }
}
