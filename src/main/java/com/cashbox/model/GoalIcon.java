package com.cashbox.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Auswahl an Standard-Symbolen für Sparziele. Die Namen entsprechen den Konstanten von
 * {@code com.vaadin.flow.component.icon.VaadinIcon}, damit die Oberfläche sie direkt darstellen kann.
 * <p>
 * In der Datenbank steht der Name ({@code savings_goal.icon}); {@code NULL} bedeutet {@link #DEFAULT}.
 * Wird eine Konstante entfernt, zeigen betroffene Ziele wieder das Standardsymbol, statt beim Laden
 * zu scheitern (siehe {@link #fromKey}).
 */
public enum GoalIcon {

    PIGGY_BANK("Sparschwein"),
    SAFE("Tresor"),
    SHIELD("Absicherung"),
    UMBRELLA("Regenschirm"),
    LIFEBUOY("Rettungsring"),
    HOME("Zuhause"),
    BED("Möbel"),
    TOOLS("Werkzeug"),
    CAR("Auto"),
    TRAIN("Bahn"),
    TRUCK("Umzug"),
    BOAT("Boot"),
    AIRPLANE("Flugzeug"),
    SUITCASE("Koffer"),
    SUN_O("Sonne"),
    GLOBE("Weltreise"),
    MAP_MARKER("Ausflug"),
    GIFT("Geschenk"),
    HEART("Herz"),
    CHILD("Kind"),
    FAMILY("Familie"),
    ACADEMY_CAP("Ausbildung"),
    BOOK("Bücher"),
    LAPTOP("Computer"),
    MOBILE("Handy"),
    CAMERA("Kamera"),
    GAMEPAD("Spiele"),
    HEADPHONES("Musik"),
    FILM("Kino"),
    CUTLERY("Essen gehen"),
    COFFEE("Kaffee"),
    GLASS("Feier"),
    TROPHY("Sport"),
    PALETTE("Hobby"),
    STETHOSCOPE("Gesundheit"),
    TOOTH("Zahnarzt"),
    GLASSES("Brille"),
    DIAMOND("Schmuck"),
    CART("Einkauf"),
    INVOICE("Rechnungen"),
    CALENDAR("Jahresausgaben"),
    STAR("Wunsch"),
    ROCKET("Großes Ziel"),
    FLAG_CHECKERED("Ziel");

    /** Symbol für Ziele ohne eigene Auswahl. */
    public static final GoalIcon DEFAULT = PIGGY_BANK;

    /** Länge der Spalte {@code savings_goal.icon}. */
    public static final int MAX_KEY_LENGTH = 40;

    private final String label;

    GoalIcon(String label) {
        this.label = label;
    }

    /** Deutscher Name für Tooltip und Screenreader. */
    public String getLabel() {
        return label;
    }

    /** Das Symbol zu einem gespeicherten Namen; leer bei {@code null} oder unbekanntem Namen. */
    public static Optional<GoalIcon> fromKey(String key) {
        return key == null ? Optional.empty()
                : Arrays.stream(values()).filter(icon -> icon.name().equals(key)).findFirst();
    }
}
