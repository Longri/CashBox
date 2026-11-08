package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

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
import com.cashbox.service.Pot;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.button.Button;
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
}
