package com.cashbox.ui;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.cashbox.model.GoalStatus;
import com.cashbox.service.GoalDetails;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Sparziel-Verwaltung: Ziele anlegen, bearbeiten, pausieren, als erreicht markieren
 * und – solange sie leer sind und keine Buchungen haben – löschen.
 * Die Ziele sind nach Status auf Reiter verteilt; ein Tipp auf ein Ziel öffnet den {@link GoalDialog}.
 * <p>
 * Die Reihenfolge (= Priorität) lässt sich ändern, indem man ein Ziel am Griff links nach oben oder
 * unten zieht – mit Maus, Finger oder per Pfeiltasten auf dem Griff (siehe {@link GoalSorting}).
 */
@Route("goals")
@PageTitle("Sparziele | CashBox")
@PermitAll
@JavaScript(GoalSorting.SCRIPT)
public class GoalsView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SavingsGoalService service;
    private final Map<GoalStatus, Tab> statusTabs = new EnumMap<>(GoalStatus.class);
    private final Tabs tabs = new Tabs();
    private final Div goalList = new Div();
    private final GoalSorting sorting;
    private List<GoalDetails> goals = List.of();

    public GoalsView(SavingsGoalService service) {
        this.service = service;
        addClassName("page-content");
        addClassName("goals-view");

        H2 title = new H2("Sparziele");
        title.addClassName("page-title");
        Button create = new Button("Neues Sparziel", VaadinIcon.PLUS.create(), e -> openDialog(null));
        create.setId("goal-new");
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div header = new Div(title, create);
        header.addClassName("page-header");

        for (GoalStatus status : GoalStatus.values()) {
            Tab tab = new Tab(status.getLabel());
            tab.setId("goals-tab-" + status.name().toLowerCase());
            statusTabs.put(status, tab);
            tabs.add(tab);
        }
        tabs.addThemeVariants(TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(e -> renderList());

        goalList.addClassName("goal-list");
        goalList.setId("goal-list");
        sorting = new GoalSorting(goalList, service, this::refresh);

        add(header, tabs, goalList);
        refresh();
    }

    private void openDialog(GoalDetails goal) {
        Runnable onSaved = goal == null
                ? () -> {
                    // Neue Ziele sind aktiv – dorthin wechseln, damit das neue Ziel sichtbar ist.
                    tabs.setSelectedTab(statusTabs.get(GoalStatus.ACTIVE));
                    refresh();
                }
                : this::refresh;
        new GoalDialog(service, goal, onSaved).open();
    }

    /** Wie nach dem Ablegen im Browser: Position innerhalb des Status speichern und neu anzeigen. */
    void moveGoal(long id, int index, boolean keyboard) {
        sorting.move(id, index, keyboard);
    }

    private void refresh() {
        goals = service.findAll();
        statusTabs.forEach((status, tab) -> tab.setLabel(
                status.getLabel() + " (" + goals.stream().filter(g -> g.status() == status).count() + ")"));
        renderList();
    }

    private GoalStatus selectedStatus() {
        return statusTabs.entrySet().stream()
                .filter(e -> e.getValue() == tabs.getSelectedTab())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(GoalStatus.ACTIVE);
    }

    private void renderList() {
        GoalStatus status = selectedStatus();
        List<GoalDetails> shown = goals.stream().filter(g -> g.status() == status).toList();

        goalList.removeAll();
        sorting.clear();
        if (goals.isEmpty()) {
            goalList.add(emptyHint("Noch keine Sparziele angelegt. Mit „Neues Sparziel“ legst du das erste an."));
        } else if (shown.isEmpty()) {
            goalList.add(emptyHint("Keine Sparziele mit Status „" + status.getLabel() + "“."));
        } else {
            GoalSorting.Handle handle = shown.size() > 1 ? GoalSorting.Handle.GRIP : GoalSorting.Handle.NONE;
            shown.forEach(goal -> {
                Div item = sorting.item(goal.id(), goal.name(), goal.status(), handle, card(goal));
                item.addClassName("goal-item--card");
                goalList.add(item);
            });
        }
    }

    private static Component emptyHint(String text) {
        Paragraph empty = new Paragraph(text);
        empty.addClassName("goal-list__empty");
        return empty;
    }

    /** Ein Ziel als antippbare Karte (natives {@code <button>}: per Tastatur erreichbar). */
    private Component card(GoalDetails goal) {
        Span name = new Span(goal.name());
        name.addClassName("goal-row__name");
        Span amounts = new Span(Money.format(goal.assignedAmount()) + " von " + Money.format(goal.targetAmount()));
        amounts.addClassName("goal-row__amounts");
        Div head = new Div(name, amounts);
        head.addClassName("goal-row__head");

        ProgressBar bar = new ProgressBar(0, 1, DashboardView.progress(goal.assignedAmount(), goal.targetAmount()));

        NativeButton card = new NativeButton();
        card.add(head, bar);

        List<String> meta = new ArrayList<>();
        if (goal.isFunded()) {
            meta.add("Zielbetrag erreicht");
        }
        if (goal.targetDate() != null) {
            meta.add("bis " + DATE.format(goal.targetDate()));
        }
        if (goal.priority() != null) {
            meta.add("Priorität " + goal.priority());
        }
        if (!meta.isEmpty()) {
            Span metaLine = new Span(String.join(" · ", meta));
            metaLine.addClassName("goal-card__meta");
            card.add(metaLine);
        }
        if (goal.description() != null) {
            Span description = new Span(goal.description());
            description.addClassName("goal-card__description");
            card.add(description);
        }

        card.setId("goal-card-" + goal.id());
        card.addClassName("goal-card");
        card.addClickListener(e -> openDialog(goal));
        return card;
    }
}
