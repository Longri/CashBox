package com.cashbox.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cashbox.service.AccountOverview;
import com.cashbox.service.BookingService;
import com.cashbox.service.Pot;
import com.vaadin.flow.component.Component;
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

        add(accountName, summary, actions, new H3("Sparziele"), goalList);
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
            Paragraph empty = new Paragraph("Noch keine Sparziele angelegt.");
            empty.addClassName("goal-list__empty");
            goalList.add(empty);
            return;
        }
        overview.goals().forEach(goal -> goalList.add(goalRow(goal)));
    }

    private static Component goalRow(AccountOverview.GoalInfo goal) {
        Span name = new Span(goal.name());
        name.addClassName("goal-row__name");
        Span amounts = new Span(Money.format(goal.assigned()) + " von " + Money.format(goal.target()));
        amounts.addClassName("goal-row__amounts");
        Div head = new Div(name, amounts);
        head.addClassName("goal-row__head");

        double progress = goal.target().signum() > 0
                ? goal.assigned().divide(goal.target(), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE).doubleValue()
                : 0;
        ProgressBar bar = new ProgressBar(0, 1, progress);

        Div row = new Div(head, bar);
        row.addClassName("goal-row");
        return row;
    }
}
