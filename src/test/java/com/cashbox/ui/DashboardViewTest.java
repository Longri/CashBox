package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.Account;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;
import com.cashbox.repository.AccountRepository;
import com.cashbox.repository.SavingsGoalRepository;
import com.cashbox.service.AccountOverview;
import com.cashbox.service.BookingService;
import com.cashbox.service.GoalDetails;
import com.cashbox.service.GoalInput;
import com.cashbox.service.Pot;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;

/** Läuft gegen die konfigurierte Datenbank; jede Methode wird zurückgerollt. */
@SpringBootTest
@Transactional
@SuppressWarnings("unchecked")
class DashboardViewTest extends SpringBrowserlessTest {

    @Autowired BookingService service;
    @Autowired AccountRepository accounts;
    @Autowired SavingsGoalRepository goals;
    @Autowired SavingsGoalService goalService;

    @BeforeEach
    void open() {
        // Angemeldeter Benutzer, sonst verweigert die Zugriffsprüfung den Zugriff auf die View
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
        navigate(DashboardView.class);
    }

    @Test
    void showsBalanceAndBuffer() {
        AccountOverview overview = service.getOverview();
        assertEquals(Money.format(overview.balance()), $(Span.class).withId("balance").single().getText());
        assertEquals(Money.format(overview.buffer()), $(Span.class).withId("buffer").single().getText());
    }

    @Test
    void depositThroughDialogUpdatesOverview() {
        BigDecimal before = service.getOverview().balance();

        test($(Button.class).withId("deposit").single()).click();
        assertEquals(1, $(BookingDialog.class).all().size());
        test($(TextField.class).withId("booking-amount").single()).setValue("12,50");
        assertEquals("12,50 € einbuchen", $(Button.class).withId("booking-save").single().getText());
        test($(Button.class).withId("booking-save").single()).click();

        assertTrue($(BookingDialog.class).all().isEmpty(), "Dialog sollte nach dem Buchen geschlossen sein");
        assertEquals(Money.format(before.add(new BigDecimal("12.50"))),
                $(Span.class).withId("balance").single().getText());
    }

    @Test
    void invalidAmountKeepsDialogOpen() {
        BigDecimal before = service.getOverview().balance();

        test($(Button.class).withId("deposit").single()).click();
        TextField amount = $(TextField.class).withId("booking-amount").single();
        test(amount).setValue("1.000");
        test($(Button.class).withId("booking-save").single()).click();

        assertFalse($(BookingDialog.class).all().isEmpty());
        assertTrue(amount.isInvalid());
        assertTrue(amount.getErrorMessage().contains("Nachkommastellen"));
        assertEquals(before, service.getOverview().balance());
    }

    @Test
    void transferThroughDialogMovesMoneyFromBufferToGoal() {
        Account account = accounts.findFirstByOrderByIdAsc().orElseThrow();
        SavingsGoal goal = goals.save(new SavingsGoal(account, "Test-Ziel-" + System.nanoTime(), new BigDecimal("100.00")));
        service.deposit(new BigDecimal("30"), null, null);
        BigDecimal bufferBefore = service.getOverview().buffer();

        test($(Button.class).withId("transfer").single()).click();
        Select<Pot> source = $(Select.class).withId("booking-source").single();
        Select<Pot> target = $(Select.class).withId("booking-target").single();
        assertTrue(source.getValue().isBuffer(), "Von sollte mit dem Puffer vorbelegt sein");
        test(target).selectItem(goal.getName());
        test($(Button.class).withId("booking-max").single()).click();
        assertEquals(Money.formatPlain(bufferBefore), $(TextField.class).withId("booking-amount").single().getValue());
        test($(Button.class).withId("booking-save").single()).click();

        assertTrue($(BookingDialog.class).all().isEmpty(), "Dialog sollte nach dem Buchen geschlossen sein");
        AccountOverview after = service.getOverview();
        assertEquals(new BigDecimal("0.00"), after.buffer());
        assertEquals(bufferBefore, after.goals().stream()
                .filter(g -> g.id().equals(goal.getId())).findFirst().orElseThrow().assigned());
    }

    @Test
    void transferSourceCannotExceedAvailableAmount() {
        Account account = accounts.findFirstByOrderByIdAsc().orElseThrow();
        SavingsGoal goal = goals.save(new SavingsGoal(account, "Test-Ziel-" + System.nanoTime(), new BigDecimal("100.00")));
        service.deposit(new BigDecimal("10"), null, null);
        BigDecimal buffer = service.getOverview().buffer();

        test($(Button.class).withId("transfer").single()).click();
        test($(Select.class).withId("booking-target").single()).selectItem(goal.getName());
        TextField amount = $(TextField.class).withId("booking-amount").single();
        test(amount).setValue(Money.formatPlain(buffer.add(new BigDecimal("0.01"))));
        test($(Button.class).withId("booking-save").single()).click();

        assertFalse($(BookingDialog.class).all().isEmpty());
        assertTrue(amount.isInvalid());
        assertTrue(amount.getErrorMessage().startsWith("Nicht genug Geld"));
    }

    @Test
    void pausedGoalIsNotOfferedAsTransferTarget() {
        Account account = accounts.findFirstByOrderByIdAsc().orElseThrow();
        SavingsGoal paused = new SavingsGoal(account, "Test-Pausiert-" + System.nanoTime(), new BigDecimal("100.00"));
        paused.setStatus(GoalStatus.PAUSED);
        goals.save(paused);
        service.deposit(new BigDecimal("10"), null, null);

        test($(Button.class).withId("transfer").single()).click();
        Select<Pot> target = $(Select.class).withId("booking-target").single();
        Pot pausedPot = target.getListDataView().getItems()
                .filter(p -> paused.getId().equals(p.goalId())).findFirst().orElseThrow();

        assertFalse(target.getItemEnabledProvider().test(pausedPot), "Pausiertes Ziel darf nicht wählbar sein");
        assertFalse(pausedPot.equals(target.getValue()), "Pausiertes Ziel darf nicht vorbelegt sein");
    }

    @Test
    void movingGoalReordersRowsWithinItsStatus() {
        String prefix = "Test-" + System.nanoTime() + "-";
        GoalDetails a = goalService.create(new GoalInput(prefix + "A", new BigDecimal("10"), null, null, null));
        GoalDetails b = goalService.create(new GoalInput(prefix + "B", new BigDecimal("10"), null, null, null));
        reopen();
        assertEquals(1, $(NativeButton.class).withId("goal-handle-" + b.id()).all().size(),
                "Aktive Ziele haben einen Ziehgriff");

        List<Long> before = shownIds(GoalStatus.ACTIVE);
        $(DashboardView.class).single().moveGoal(b.id(), before.indexOf(a.id()), false);

        List<Long> after = shownIds(GoalStatus.ACTIVE);
        assertEquals(after.indexOf(b.id()) + 1, after.indexOf(a.id()), "B steht jetzt direkt vor A");
        assertEquals(after, goalService.findAll().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE).map(GoalDetails::id).toList());
    }

    @Test
    void rowsStayAlignedWhenSomeStatusHasNothingToSort() {
        Account account = accounts.findFirstByOrderByIdAsc().orElseThrow();
        goals.save(new SavingsGoal(account, "Test-Aktiv1-" + System.nanoTime(), new BigDecimal("10.00")));
        goals.save(new SavingsGoal(account, "Test-Aktiv2-" + System.nanoTime(), new BigDecimal("10.00")));
        SavingsGoal paused = new SavingsGoal(account, "Test-Pausiert-" + System.nanoTime(), new BigDecimal("10.00"));
        paused.setStatus(GoalStatus.PAUSED);
        goals.save(paused);
        reopen();

        // Jede Zeile beginnt mit Griff oder gleich breitem Leerraum – nie mit dem Inhalt.
        goalList().getChildren().forEach(item -> {
            String first = item.getChildren().findFirst().orElseThrow().getElement().getAttribute("class");
            assertTrue(first.contains("goal-item__handle") || first.contains("goal-item__spacer"),
                    "Zeile " + item.getElement().getAttribute("data-goal-id") + " beginnt mit „" + first + "“");
        });
    }

    /** Neu aufbauen, damit gerade angelegte Ziele erscheinen (dieselbe Route würde die View wiederverwenden). */
    private void reopen() {
        navigate(GoalsView.class);
        navigate(DashboardView.class);
    }

    private Div goalList() {
        return $(Div.class).withId("dashboard-goal-list").single();
    }

    /** IDs der Zeilen eines Status in der angezeigten Reihenfolge. */
    private List<Long> shownIds(GoalStatus status) {
        return goalList().getChildren()
                .filter(item -> status.name().equals(item.getElement().getAttribute("data-sort-group")))
                .map(item -> Long.valueOf(item.getElement().getAttribute("data-goal-id")))
                .toList();
    }
}
