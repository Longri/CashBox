package com.cashbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.GoalStatus;
import com.cashbox.repository.SavingsGoalRepository;

/** Läuft gegen die konfigurierte Datenbank; jede Methode wird zurückgerollt. */
@SpringBootTest
@Transactional
class SavingsGoalServiceTest {

    @Autowired SavingsGoalService service;
    @Autowired BookingService bookingService;
    @Autowired SavingsGoalRepository goals;

    /** Eindeutiger Namensanfang, damit vorhandene Ziele in der Datenbank nicht stören. */
    private String prefix;

    @BeforeEach
    void setUp() {
        prefix = "Test-" + System.nanoTime() + "-";
    }

    @Test
    void createsActiveEmptyGoalWithCleanedInput() {
        GoalDetails goal = service.create(new GoalInput("  " + prefix + "Urlaub  ", new BigDecimal("1500.5"),
                LocalDate.of(2027, 6, 1), 2, "   "));

        assertNotNull(goal.id());
        assertNotNull(goal.version());
        assertEquals(prefix + "Urlaub", goal.name());
        assertEquals(eur("1500.50"), goal.targetAmount());
        assertEquals(0, goal.assignedAmount().signum());
        assertEquals(LocalDate.of(2027, 6, 1), goal.targetDate());
        assertEquals(2, goal.priority());
        assertNull(goal.description(), "Leere Beschreibung wird als null gespeichert");
        assertEquals(GoalStatus.ACTIVE, goal.status());
        assertTrue(service.findAll().stream().anyMatch(g -> g.id().equals(goal.id())));
    }

    @Test
    void rejectsDuplicateNamesIgnoringCaseAndAccents() {
        create(prefix + "Fahrrad");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> create(prefix + "fahrrad"));
        assertEquals("Es gibt bereits ein Sparziel mit dem Namen „" + prefix + "fahrrad“.", ex.getMessage());
        // Die Kollation utf8mb4_unicode_ci behandelt „ä“ wie „a“ – genau wie der eindeutige Index.
        assertThrows(IllegalArgumentException.class, () -> create(prefix + "Fährrad"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> create("   "));
        assertThrows(IllegalArgumentException.class, () -> create(null));
        assertThrows(IllegalArgumentException.class, () -> create("x".repeat(SavingsGoalService.MAX_NAME_LENGTH + 1)));

        assertThrows(IllegalArgumentException.class, () -> service.create(input(prefix + "A", null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(prefix + "A", BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(prefix + "A", new BigDecimal("1.005"))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(input(prefix + "A", new BigDecimal("-5"))));
        assertEquals("Der Zielbetrag muss größer als 0 sein.", ex.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new GoalInput(prefix + "A", eur("10"), null, 0, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new GoalInput(prefix + "A", eur("10"), null, SavingsGoalService.MAX_PRIORITY + 1, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new GoalInput(prefix + "A", eur("10"),
                null, null, "x".repeat(SavingsGoalService.MAX_DESCRIPTION_LENGTH + 1))));
    }

    @Test
    void updatesFieldsAndStatusButKeepsAssignedAmount() {
        GoalDetails goal = create(prefix + "Auto");
        bookingService.deposit(eur("40"), goal.id(), null);
        goal = fresh(goal.id());

        GoalDetails updated = service.update(goal.id(), goal.version(), new GoalInput(prefix + "AUTO",
                eur("8000"), LocalDate.of(2028, 1, 31), 5, "  Gebrauchtwagen  "), GoalStatus.PAUSED);

        assertEquals(prefix + "AUTO", updated.name(), "Eigenen Namen anders schreiben ist erlaubt");
        assertEquals(eur("8000.00"), updated.targetAmount());
        assertEquals(eur("40.00"), updated.assignedAmount());
        assertEquals(LocalDate.of(2028, 1, 31), updated.targetDate());
        assertEquals(5, updated.priority());
        assertEquals("Gebrauchtwagen", updated.description());
        assertEquals(GoalStatus.PAUSED, updated.status());
        assertTrue(updated.version() > goal.version());
    }

    @Test
    void updateRejectsNameOfAnotherGoal() {
        create(prefix + "Urlaub");
        GoalDetails other = create(prefix + "Reise");

        assertThrows(IllegalArgumentException.class, () -> service.update(other.id(), other.version(),
                input(prefix + "urlaub", eur("10")), GoalStatus.ACTIVE));
    }

    @Test
    void updateWithStaleVersionFails() {
        GoalDetails goal = create(prefix + "Laptop");
        service.update(goal.id(), goal.version(), input(prefix + "Laptop", eur("1200")), GoalStatus.ACTIVE);

        assertThrows(OptimisticLockingFailureException.class, () -> service.update(goal.id(), goal.version(),
                input(prefix + "Laptop", eur("1300")), GoalStatus.ACTIVE));
    }

    @Test
    void deletesEmptyGoalWithoutBookings() {
        GoalDetails goal = create(prefix + "Weg damit");
        assertTrue(service.canDelete(goal.id()));

        service.delete(goal.id(), goal.version());

        assertFalse(goals.existsById(goal.id()));
    }

    @Test
    void refusesToDeleteGoalWithMoney() {
        GoalDetails goal = create(prefix + "Voll");
        bookingService.deposit(eur("10"), goal.id(), null);

        assertFalse(service.canDelete(goal.id()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.delete(goal.id(), fresh(goal.id()).version()));
        assertTrue(ex.getMessage().contains("enthält noch Geld"), ex.getMessage());
    }

    @Test
    void refusesToDeleteEmptyGoalWithBookings() {
        GoalDetails goal = create(prefix + "Leer mit Historie");
        bookingService.deposit(eur("10"), goal.id(), null);
        bookingService.transfer(goal.id(), null, eur("10"), null);

        assertFalse(service.canDelete(goal.id()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.delete(goal.id(), fresh(goal.id()).version()));
        assertTrue(ex.getMessage().contains("hat bereits Buchungen"), ex.getMessage());
    }

    @Test
    void inactiveGoalsTakeNoMoneyButCanBeSource() {
        GoalDetails goal = create(prefix + "Pausiert");
        bookingService.deposit(eur("20"), goal.id(), null);
        bookingService.deposit(eur("5"), null, null);
        goal = fresh(goal.id());
        service.update(goal.id(), goal.version(), input(goal.name(), goal.targetAmount()), GoalStatus.PAUSED);
        Long id = goal.id();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.transfer(null, id, eur("5"), null));
        assertTrue(ex.getMessage().contains("pausiert"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> bookingService.deposit(eur("5"), id, null));

        bookingService.transfer(id, null, eur("20"), "zurück in den Puffer");
        assertEquals(0, fresh(id).assignedAmount().signum());
    }

    @Test
    void listsActiveGoalsFirstThenByPriorityAndName() {
        GoalDetails paused = create(prefix + "A-pausiert");
        service.update(paused.id(), paused.version(), new GoalInput(paused.name(), eur("10"), null, 1, null),
                GoalStatus.PAUSED);
        create(prefix + "B-ohne-Prio");
        service.create(new GoalInput(prefix + "C-Prio-2", eur("10"), null, 2, null));
        service.create(new GoalInput(prefix + "D-Prio-1", eur("10"), null, 1, null));

        List<String> names = service.findAll().stream()
                .map(GoalDetails::name)
                .filter(n -> n.startsWith(prefix))
                .map(n -> n.substring(prefix.length()))
                .toList();
        assertEquals(List.of("D-Prio-1", "C-Prio-2", "B-ohne-Prio", "A-pausiert"), names);
    }

    // ------------------------------------------------------------------ Hilfen

    private GoalDetails create(String name) {
        return service.create(input(name, eur("100")));
    }

    private static GoalInput input(String name, BigDecimal target) {
        return new GoalInput(name, target, null, null, null);
    }

    /** Aktueller Stand inkl. Version (die Abfrage schreibt offene Änderungen vorher in die Datenbank). */
    private GoalDetails fresh(Long id) {
        return service.findAll().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
    }

    private static BigDecimal eur(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
