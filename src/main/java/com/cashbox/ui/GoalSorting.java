package com.cashbox.ui;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;

import com.cashbox.model.GoalStatus;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import tools.jackson.databind.JsonNode;

/**
 * Sparziele per Ziehen umsortieren – gemeinsam genutzt von {@link GoalsView} und {@link DashboardView}.
 * <p>
 * Jeder Listeneintrag bekommt links einen Ziehgriff. Das Ziehen (Maus, Finger, Pfeiltasten) erledigt
 * {@code goal-sort.js} im Browser; verschoben wird nur innerhalb derselben Sortiergruppe, also
 * innerhalb eines Status. Nach dem Ablegen meldet das Skript {@code goal-move} an die Liste; hier wird
 * gespeichert ({@link SavingsGoalService#move}) und die Liste über {@code refresh} neu aufgebaut.
 * <p>
 * Views, die das nutzen, laden das Skript mit {@code @JavaScript(GoalSorting.SCRIPT)}.
 */
final class GoalSorting {

    /** Statische Datei aus {@code META-INF/resources}; in der {@code SecurityConfig} freigegeben. */
    static final String SCRIPT = "context://goal-sort.js";

    /** Linker Rand eines Listeneintrags. */
    enum Handle {
        /** Nichts – es gibt nichts zu sortieren. */
        NONE,
        /** Ziehgriff. */
        GRIP,
        /** Leerer Platz in Griffbreite, damit ein Eintrag ohne Griff bündig mit den anderen steht. */
        SPACER
    }

    private final SavingsGoalService service;
    private final Runnable refresh;
    /** Griffe der angezeigten Ziele, damit nach Verschieben per Tastatur der Fokus zurückkommt. */
    private final Map<Long, NativeButton> handles = new HashMap<>();

    /**
     * @param list    die Liste, deren direkte Kinder mit {@link #item} erzeugt werden
     * @param refresh baut die Liste nach dem Verschieben neu auf (und ruft dabei {@link #clear()})
     */
    GoalSorting(Div list, SavingsGoalService service, Runnable refresh) {
        this.service = service;
        this.refresh = refresh;
        list.getElement().addEventListener("goal-move", e -> {
                    JsonNode data = e.getEventData();
                    move(data.get("event.detail.id").asLong(), data.get("event.detail.index").asInt(),
                            data.get("event.detail.keyboard").asBoolean());
                })
                .addEventData("event.detail.id")
                .addEventData("event.detail.index")
                .addEventData("event.detail.keyboard");
    }

    /** Vergisst die Griffe der bisherigen Einträge; vor dem Neuaufbau der Liste aufrufen. */
    void clear() {
        handles.clear();
    }

    /**
     * Ein Listeneintrag: Griff (oder Platzhalter) und Inhalt.
     *
     * @param status Sortiergruppe – im Browser wird nur zwischen Einträgen gleichen Status verschoben
     */
    Div item(Long goalId, String goalName, GoalStatus status, Handle handle, Component content) {
        Div item = new Div();
        item.addClassName("goal-item");
        item.getElement().setAttribute("data-goal-id", String.valueOf(goalId));
        item.getElement().setAttribute("data-sort-group", status.name());
        switch (handle) {
            case GRIP -> item.add(grip(goalId, goalName));
            case SPACER -> {
                Div spacer = new Div();
                spacer.addClassName("goal-item__spacer");
                item.add(spacer);
            }
            case NONE -> {
                // kein linker Rand
            }
        }
        item.add(content);
        return item;
    }

    /**
     * Speichert die neue Position eines Ziels und baut die Liste neu auf. Schlägt das fehl,
     * zeigt die neu geladene Liste wieder die gespeicherte Reihenfolge.
     *
     * @param index Position innerhalb des Status (0 = ganz oben)
     */
    void move(long goalId, int index, boolean keyboard) {
        try {
            service.move(goalId, index);
        } catch (OptimisticLockingFailureException | IllegalArgumentException | IllegalStateException ex) {
            String text = ex instanceof OptimisticLockingFailureException
                    ? "Ein Sparziel wurde zwischenzeitlich geändert. Die Liste ist jetzt aktuell – bitte erneut verschieben."
                    : ex.getMessage();
            Notification notification = Notification.show(text, 5000, Notification.Position.BOTTOM_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
        refresh.run();
        NativeButton handle = handles.get(goalId);
        if (keyboard && handle != null) {
            handle.focus();
        }
    }

    private NativeButton grip(Long goalId, String goalName) {
        NativeButton handle = new NativeButton();
        handle.setId("goal-handle-" + goalId);
        handle.addClassName("goal-item__handle");
        handle.setAriaLabel("„" + goalName + "“ verschieben");
        handle.getElement().setAttribute("title", "Ziehen (oder Pfeiltasten ↑ ↓), um die Reihenfolge zu ändern");
        handles.put(goalId, handle);
        return handle;
    }
}
