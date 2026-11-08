package com.cashbox.ui.theme;

import java.util.List;

import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

/** Die beiden mitgelieferten Vaadin-Themes. */
public enum ThemeFamily {

    /** theme-lumo.css: Akzentfarben. Kompakt: Vaadins eigenes Preset. */
    LUMO("lumo", "Lumo", "Das klassische Vaadin-Theme",
            List.of(Lumo.STYLESHEET, "theme-lumo.css"), Lumo.COMPACT_STYLESHEET),

    /**
     * theme-aura.css: Aura kennt die {@code --lumo-*}-Variablen nicht, auf denen styles.css aufbaut;
     * die Datei bildet sie auf Aura-Werte ab und enthält die Akzentfarben.
     */
    AURA("aura", "Aura", "Das neue Vaadin-Theme, etwas luftiger",
            List.of(Aura.STYLESHEET, "theme-aura.css"), "theme-aura-compact.css");

    private final String key;
    private final String label;
    private final String description;
    private final List<String> styleSheets;
    private final String compactStyleSheet;

    ThemeFamily(String key, String label, String description, List<String> styleSheets, String compactStyleSheet) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.styleSheets = styleSheets;
        this.compactStyleSheet = compactStyleSheet;
    }

    /** Wert im Cookie. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Stylesheets relativ zur Anwendungswurzel, in Ladereihenfolge. */
    public List<String> styleSheets() {
        return styleSheets;
    }

    /** Zusätzlich für die kompakte Ansicht, nach {@link #styleSheets()} geladen. */
    public String compactStyleSheet() {
        return compactStyleSheet;
    }

    static ThemeFamily fromKey(String key, ThemeFamily fallback) {
        for (ThemeFamily family : values()) {
            if (family.key.equals(key)) {
                return family;
            }
        }
        return fallback;
    }
}
