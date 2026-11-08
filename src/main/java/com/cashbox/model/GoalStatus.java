package com.cashbox.model;

/**
 * Status eines Sparziels (Spalte {@code savings_goal.status}).
 * Die Reihenfolge der Konstanten ist die Anzeigereihenfolge; gespeichert wird der Name
 * ({@code EnumType.STRING}), Umsortieren ist also unkritisch.
 */
public enum GoalStatus {
    /** Normaler Zustand: Geld kann zugewiesen und entnommen werden. */
    ACTIVE("Aktiv"),
    /** Vorübergehend ruhend: kein neues Geld, Entnahmen bleiben möglich. */
    PAUSED("Pausiert"),
    /** Abgeschlossen: kein neues Geld, Restgeld kann zurückgebucht werden. */
    REACHED("Erreicht");

    private final String label;

    GoalStatus(String label) {
        this.label = label;
    }

    /** Anzeigename für die Oberfläche. */
    public String getLabel() {
        return label;
    }

    /** Nur aktive Ziele dürfen neues Geld bekommen. */
    public boolean acceptsMoney() {
        return this == ACTIVE;
    }
}
