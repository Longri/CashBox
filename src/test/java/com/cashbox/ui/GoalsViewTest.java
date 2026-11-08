package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.GoalStatus;
import com.cashbox.service.BookingService;
import com.cashbox.service.GoalDetails;
import com.cashbox.service.GoalInput;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

/** Läuft gegen die konfigurierte Datenbank; jede Methode wird zurückgerollt. */
@SpringBootTest
@Transactional
@SuppressWarnings("unchecked")
class GoalsViewTest extends SpringBrowserlessTest {

    @Autowired SavingsGoalService service;
    @Autowired BookingService bookingService;

    private String prefix;

    @BeforeEach
    void login() {
        // Angemeldeter Benutzer, sonst verweigert die Zugriffsprüfung den Zugriff auf die View
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
        prefix = "Test-" + System.nanoTime() + "-";
    }

    @Test
    void createsGoalThroughDialog() {
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        assertEquals(1, $(GoalDialog.class).all().size());
        test($(TextField.class).withId("goal-name").single()).setValue(prefix + "Urlaub");
        test($(TextField.class).withId("goal-target").single()).setValue("1500,5");
        test($(IntegerField.class).withId("goal-priority").single()).setValue(3);
        test($(Button.class).withId("goal-save").single()).click();

        assertTrue($(GoalDialog.class).all().isEmpty(), "Dialog sollte nach dem Anlegen geschlossen sein");
        GoalDetails created = find(prefix + "Urlaub");
        assertEquals(new BigDecimal("1500.50"), created.targetAmount());
        assertEquals(3, created.priority());
        assertEquals(GoalStatus.ACTIVE, created.status());
        assertEquals(1, card(created).size(), "Neues Ziel sollte im Reiter „Aktiv“ stehen");
    }

    @Test
    void missingNameKeepsDialogOpen() {
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        test($(TextField.class).withId("goal-target").single()).setValue("100");
        test($(Button.class).withId("goal-save").single()).click();

        assertFalse($(GoalDialog.class).all().isEmpty());
        assertTrue($(TextField.class).withId("goal-name").single().isInvalid());
    }

    @Test
    void duplicateNameShowsErrorInDialog() {
        service.create(new GoalInput(prefix + "Fahrrad", new BigDecimal("500"), null, null, null));
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        test($(TextField.class).withId("goal-name").single()).setValue(prefix + "FAHRRAD");
        test($(TextField.class).withId("goal-target").single()).setValue("100");
        test($(Button.class).withId("goal-save").single()).click();

        assertFalse($(GoalDialog.class).all().isEmpty());
    }

    @Test
    void pausingMovesGoalToPausedTab() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Auto", new BigDecimal("8000"), null, null, null));
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();
        RadioButtonGroup<GoalStatus> status = $(RadioButtonGroup.class).withId("goal-status").single();
        assertEquals(GoalStatus.ACTIVE, status.getValue());
        test(status).selectItem(GoalStatus.PAUSED.getLabel());
        test($(Button.class).withId("goal-save").single()).click();

        assertTrue($(GoalDialog.class).all().isEmpty());
        assertEquals(GoalStatus.PAUSED, find(goal.name()).status());
        assertTrue(card(goal).isEmpty(), "Pausiertes Ziel gehört nicht mehr in „Aktiv“");
        test($(Tabs.class).single()).select(GoalStatus.PAUSED.ordinal());
        assertEquals(1, card(goal).size());
    }

    @Test
    void deletesEmptyGoalAfterConfirmation() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Weg", new BigDecimal("50"), null, null, null));
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();
        test($(Button.class).withId("goal-delete").single()).click();
        test($(ConfirmDialog.class).single()).confirm();

        assertTrue($(GoalDialog.class).all().isEmpty());
        assertNull(findOrNull(goal.name()));
        assertTrue(card(goal).isEmpty());
    }

    @Test
    void goalWithBookingsOffersNoDelete() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Mit Geld", new BigDecimal("50"), null, null, null));
        bookingService.deposit(new BigDecimal("10"), goal.id(), null);
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();

        assertTrue($(Button.class).withId("goal-delete").all().isEmpty());
        assertEquals(1, $(Paragraph.class).withId("goal-delete-hint").all().size());
        assertEquals("Bereits zugewiesen: " + Money.format(new BigDecimal("10.00")),
                $(TextField.class).withId("goal-target").single().getHelperText());
    }

    // ------------------------------------------------------------------ Hilfen

    private java.util.List<NativeButton> card(GoalDetails goal) {
        return $(NativeButton.class).withId("goal-card-" + goal.id()).all();
    }

    private GoalDetails find(String name) {
        GoalDetails goal = findOrNull(name);
        assertTrue(goal != null, "Sparziel „" + name + "“ nicht gefunden");
        return goal;
    }

    private GoalDetails findOrNull(String name) {
        return service.findAll().stream().filter(g -> g.name().equals(name)).findFirst().orElse(null);
    }
}
