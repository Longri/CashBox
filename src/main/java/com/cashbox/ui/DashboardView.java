package com.cashbox.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cashbox.service.AccountOverview;
import com.cashbox.service.BookingService;
import com.cashbox.service.Pot;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
 * Startseite: Kontostand, Puffer, Sparziele und der Einstieg in Ein- und Umbuchungen.
 */
@Route("")
@PageTitle("Übersicht | CashBox")
@PermitAll
public class DashboardView extends VerticalLayout {

    private final BookingService bookingService;
    private final H2 accountName = new H2();
    private final Span balance = new Span();
    private final Span buffer = new Span();
    private final Div goalList = new Div();

    public DashboardView(BookingService bookingService) {
        this.bookingService = bookingService;
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
        Div actions = new Div(deposit, transfer);
        actions.addClassName("booking-actions");

        goalList.addClassName("goal-list");
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

    private void refresh() {
        AccountOverview overview = bookingService.getOverview();
        accountName.setText(overview.accountName());
        balance.setText(Money.format(overview.balance()));
        buffer.setText(Money.format(overview.buffer()));

        goalList.removeAll();
        if (overview.goals().isEmpty()) {
            RouterLink create = new RouterLink("Sparziel anlegen", GoalsView.class);
            create.setId("goals-create-link");
            Paragraph empty = new Paragraph(new Text("Noch keine Sparziele angelegt. "), create);
            empty.addClassName("goal-list__empty");
            goalList.add(empty);
            return;
        }
        overview.goals().forEach(goal -> goalList.add(goalRow(goal)));
    }

    /** Eine Zielzeile; ein Tipp darauf führt zur Sparziel-Verwaltung. */
    private static Component goalRow(AccountOverview.GoalInfo goal) {
        Span name = new Span(goal.name());
        name.addClassName("goal-row__name");
        Div title = new Div(name);
        title.addClassName("goal-row__title");
        if (!goal.status().acceptsMoney()) {
            Span badge = new Span(goal.status().getLabel());
            badge.addClassName("goal-status-badge");
            title.add(badge);
        }
        Span amounts = new Span(Money.format(goal.assigned()) + " von " + Money.format(goal.target()));
        amounts.addClassName("goal-row__amounts");
        Div head = new Div(title, amounts);
        head.addClassName("goal-row__head");

        ProgressBar bar = new ProgressBar(0, 1, progress(goal.assigned(), goal.target()));

        RouterLink row = new RouterLink(GoalsView.class);
        row.add(head, bar);
        row.addClassNames("goal-row", "goal-row--link");
        row.setClassName("goal-row--inactive", !goal.status().acceptsMoney());
        return row;
    }

    /** Fortschritt für die {@link ProgressBar}: 0 bis 1, bei Überfüllung gedeckelt. */
    static double progress(BigDecimal assigned, BigDecimal target) {
        return target.signum() > 0
                ? assigned.divide(target, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE).doubleValue()
                : 0;
    }
}
