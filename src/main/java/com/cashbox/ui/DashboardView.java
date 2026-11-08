package com.cashbox.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cashbox.model.GoalStatus;
import com.cashbox.service.AccountOverview;
import com.cashbox.service.BookingService;
import com.cashbox.service.Pot;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.PermitAll;

/**
 * Startseite: Kontostand, Puffer, die laufenden Sparziele und der Einstieg in Ein-, Um- und Ausbuchungen.
 * <p>
 * Gezeigt werden nur aktive Sparziele; pausierte und erreichte stehen auf der Sparziel-Seite.
 * Die Ziele lassen sich wie dort am Griff umsortieren ({@link GoalSorting}).
 */
@Route("")
@PageTitle("Übersicht | CashBox")
@PermitAll
@JavaScript(GoalSorting.SCRIPT)
public class DashboardView extends VerticalLayout {

    private final BookingService bookingService;
    private final SavingsGoalService goalService;
    private final H2 accountName = new H2();
    private final Span balance = new Span();
    private final Span buffer = new Span();
    private final Div goalList = new Div();
    private final GoalSorting sorting;

    public DashboardView(BookingService bookingService, SavingsGoalService goalService) {
        this.bookingService = bookingService;
        this.goalService = goalService;
        addClassName("page-content");
        addClassName("dashboard");

        balance.setId("balance");
        buffer.setId("buffer");
        Div summary = new Div(card("Kontostand", balance), card(Pot.BUFFER_NAME + " (nicht zugeordnet)", buffer));
        summary.addClassName("summary-cards");

        Button deposit = new Button("Einbuchen", VaadinIcon.PLUS.create(), e -> openDialog(BookingDialog.Mode.DEPOSIT));
        deposit.setId("deposit");
        deposit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button transfer = new Button("Umbuchen", VaadinIcon.EXCHANGE.create(), e -> openDialog(BookingDialog.Mode.TRANSFER));
        transfer.setId("transfer");
        Button withdraw = new Button("Ausbuchen", VaadinIcon.MINUS.create(), e -> openDialog(BookingDialog.Mode.WITHDRAWAL));
        withdraw.setId("withdraw");
        Div actions = new Div(deposit, transfer, withdraw);
        actions.addClassName("booking-actions");

        goalList.addClassName("goal-list");
        goalList.setId("dashboard-goal-list");
        sorting = new GoalSorting(goalList, goalService, this::refresh);
        accountName.addClassName("dashboard-title");

        RouterLink manage = new RouterLink("Verwalten", GoalsView.class);
        manage.setId("goals-manage");
        Div goalsHeader = new Div(new H3("Sparziele"), manage);
        goalsHeader.addClassName("section-header");

        add(accountName, summary, actions, goalsHeader, goalList);
        refresh();
    }

    private static Component card(String label, Span value) {
        Span caption = new Span(label);
        caption.addClassName("summary-card__label");
        value.addClassName("summary-card__value");
        Div card = new Div(caption, value);
        card.addClassName("summary-card");
        return card;
    }

    private void openDialog(BookingDialog.Mode mode) {
        new BookingDialog(bookingService, mode, this::refresh).open();
    }

    /** Wie nach dem Ablegen im Browser: Position innerhalb des Status speichern und neu anzeigen. */
    void moveGoal(long id, int index, boolean keyboard) {
        sorting.move(id, index, keyboard);
    }

    private void refresh() {
        AccountOverview overview = bookingService.getOverview();
        accountName.setText(overview.accountName());
        balance.setText(Money.format(overview.balance()));
        buffer.setText(Money.format(overview.buffer()));

        goalList.removeAll();
        sorting.clear();
        List<AccountOverview.GoalInfo> active = overview.goals().stream()
                .filter(goal -> goal.status() == GoalStatus.ACTIVE)
                .toList();
        if (active.isEmpty()) {
            boolean hasGoals = !overview.goals().isEmpty();
            RouterLink link = new RouterLink(hasGoals ? "Sparziele verwalten" : "Sparziel anlegen", GoalsView.class);
            link.setId("goals-create-link");
            Paragraph empty = new Paragraph(
                    new Text(hasGoals ? "Gerade läuft kein Sparziel. " : "Noch keine Sparziele angelegt. "), link);
            empty.addClassName("goal-list__empty");
            goalList.add(empty);
            return;
        }
        GoalSorting.Handle handle = active.size() > 1 ? GoalSorting.Handle.GRIP : GoalSorting.Handle.NONE;
        active.forEach(goal -> {
            Div item = sorting.item(goal.id(), goal.name(), goal.status(), handle, goalRow(goal));
            item.addClassName("goal-item--row");
            goalList.add(item);
        });
    }

    /** Eine Zielzeile; ein Tipp darauf führt zur Sparziel-Verwaltung. */
    private Component goalRow(AccountOverview.GoalInfo goal) {
        Span name = new Span(goal.name());
        name.addClassName("goal-row__name");
        Div title = new Div(name);
        title.addClassName("goal-row__title");
        Span amounts = new Span(Money.format(goal.assigned()) + " von " + Money.format(goal.target()));
        amounts.addClassName("goal-row__amounts");
        Div head = new Div(title, amounts);
        head.addClassName("goal-row__head");

        ProgressBar bar = new ProgressBar(0, 1, progress(goal.assigned(), goal.target()));

        Div body = new Div(head, bar);
        body.addClassName("goal-row__body");
        RouterLink row = new RouterLink(GoalsView.class);
        row.add(GoalAvatar.of(goal.id(), goal.icon(), goal.hasImage(), goalService, GoalAvatar.Size.SMALL), body);
        row.addClassNames("goal-row", "goal-row--link");
        return row;
    }

    /** Fortschritt für die {@link ProgressBar}: 0 bis 1, bei Überfüllung gedeckelt. */
    static double progress(BigDecimal assigned, BigDecimal target) {
        return target.signum() > 0
                ? assigned.divide(target, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE).doubleValue()
                : 0;
    }
}
