package com.cashbox.ui.theme;

import java.util.List;

import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

/** Die beiden mitgelieferten Vaadin-Themes. */
public enum ThemeFamily {

    LUMO("lumo", "Lumo", "Das klassische Vaadin-Theme", List.of(Lumo.STYLESHEET)),

    /**
     * Aura kennt die {@code --lumo-*}-Variablen nicht, auf denen styles.css aufbaut;
     * aura-compat.css bildet sie auf Aura-Werte ab.
     */
    AURA("aura", "Aura", "Das neue Vaadin-Theme, etwas luftiger", List.of(Aura.STYLESHEET, "aura-compat.css"));

    private final String key;
    private final String label;
    private final String description;
    private final List<String> styleSheets;

    ThemeFamily(String key, String label, String description, List<String> styleSheets) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.styleSheets = styleSheets;
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

    static ThemeFamily fromKey(String key, ThemeFamily fallback) {
        for (ThemeFamily family : values()) {
            if (family.key.equals(key)) {
                return family;
            }
        }
        return fallback;
    }
}
