package com.cashbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.Account;
import com.cashbox.model.Booking;
import com.cashbox.model.BookingType;
import com.cashbox.model.GoalStatus;
import com.cashbox.model.SavingsGoal;
import com.cashbox.repository.AccountRepository;
import com.cashbox.repository.BookingRepository;
import com.cashbox.repository.SavingsGoalRepository;

/** Läuft gegen die konfigurierte Datenbank; jede Methode wird zurückgerollt. */
@SpringBootTest
@Transactional
class BookingServiceTest {

    @Autowired BookingService service;
    @Autowired AccountRepository accounts;
    @Autowired SavingsGoalRepository goals;
    @Autowired BookingRepository bookings;

    private Account account;
    private SavingsGoal vacation;
    private SavingsGoal bike;
    private BigDecimal balanceBefore;
    private BigDecimal bufferBefore;
    private int bookingsBefore;

    @BeforeEach
    void setUp() {
        account = accounts.findFirstByOrderByIdAsc().orElseThrow();
        long suffix = System.nanoTime();
        vacation = goals.save(new SavingsGoal(account, "Test-Urlaub-" + suffix, new BigDecimal("1000.00")));
        bike = goals.save(new SavingsGoal(account, "Test-Fahrrad-" + suffix, new BigDecimal("500.00")));
        balanceBefore = account.getCurrentBalance();
        bufferBefore = account.getUnassignedBuffer();
        bookingsBefore = allBookings().size();
    }

    @Test
    void depositGoesToBuffer() {
        service.deposit(new BigDecimal("100"), null, "  Gehalt  ");

        assertEquals(balanceBefore.add(eur("100")), account.getCurrentBalance());
        assertEquals(bufferBefore.add(eur("100")), account.getUnassignedBuffer());
        List<Booking> all = allBookings();
        assertEquals(bookingsBefore + 1, all.size());
        Booking booking = all.getFirst();
        assertEquals(BookingType.DEPOSIT, booking.getType());
        assertEquals(eur("100"), booking.getAmount());
        assertEquals("Gehalt", booking.getNote());
    }

    @Test
    void depositDirectlyIntoGoalCreatesDepositAndTransfer() {
        service.deposit(new BigDecimal("50.5"), vacation.getId(), null);

        assertEquals(balanceBefore.add(eur("50.50")), account.getCurrentBalance());
        assertEquals(bufferBefore, account.getUnassignedBuffer());
        assertEquals(eur("50.50"), vacation.getAssignedAmount());
        assertEquals(bookingsBefore + 2, allBookings().size());
    }

    @Test
    void transfersBetweenBufferAndGoals() {
        service.deposit(eur("100"), null, null);
        service.transfer(null, vacation.getId(), eur("60"), null);
        service.transfer(vacation.getId(), bike.getId(), eur("25"), null);
        service.transfer(bike.getId(), null, eur("5"), "zurück");

        assertEquals(balanceBefore.add(eur("100")), account.getCurrentBalance());
        assertEquals(bufferBefore.add(eur("45")), account.getUnassignedBuffer());
        assertEquals(eur("35"), vacation.getAssignedAmount());
        assertEquals(eur("20"), bike.getAssignedAmount());
        assertEquals(bookingsBefore + 4, allBookings().size());
    }

    @Test
    void rejectsTransferBeyondAvailableAmount() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(vacation.getId(), bike.getId(), eur("0.01"), null));
        assertEquals("Im Sparziel „" + vacation.getName() + "“ ist nicht genug Geld.", ex.getMessage());
        assertEquals(0, bike.getAssignedAmount().signum());
    }

    @Test
    void rejectsIdenticalSourceAndTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(vacation.getId(), vacation.getId(), eur("1"), null));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(null, null, eur("1"), null));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.deposit(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(BigDecimal.ZERO, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(new BigDecimal("-1"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(new BigDecimal("1.005"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(BookingService.MAX_AMOUNT, null, null));
    }

    @Test
    void withdrawFromBufferReducesBalanceAndBuffer() {
        service.deposit(eur("100"), null, null);
        service.withdrawFromBuffer(new BigDecimal("40.5"), "Waschmaschine");

        assertEquals(balanceBefore.add(eur("59.50")), account.getCurrentBalance());
        assertEquals(bufferBefore.add(eur("59.50")), account.getUnassignedBuffer());
        Booking booking = allBookings().getFirst();
        assertEquals(BookingType.WITHDRAWAL, booking.getType());
        assertEquals(eur("40.50"), booking.getAmount());
        assertNull(booking.getSourceGoal());
        assertNull(booking.getTargetGoal());
        assertEquals("Waschmaschine", booking.getNote());
    }

    @Test
    void rejectsBufferWithdrawalBeyondBuffer() {
        BigDecimal tooMuch = bufferBefore.add(eur("0.01"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.withdrawFromBuffer(tooMuch, null));
        assertEquals("Im Puffer ist nicht genug Geld.", ex.getMessage());
        assertEquals(balanceBefore, account.getCurrentBalance());
    }

    @Test
    void withdrawGoalTakesWholeAmountAndMarksItReached() {
        service.deposit(eur("80"), vacation.getId(), null);
        service.withdrawGoal(vacation.getId(), eur("80"), true, "Flug gebucht");

        assertEquals(balanceBefore, account.getCurrentBalance());
        assertEquals(bufferBefore, account.getUnassignedBuffer());
        assertEquals(0, vacation.getAssignedAmount().signum());
        assertEquals(GoalStatus.REACHED, vacation.getStatus());
        Booking booking = allBookings().getFirst();
        assertEquals(BookingType.WITHDRAWAL, booking.getType());
        assertEquals(eur("80"), booking.getAmount());
        assertEquals(vacation.getId(), booking.getSourceGoal().getId());
        assertNull(booking.getTargetGoal());
    }

    @Test
    void withdrawGoalCanKeepStatusAndWorksForPausedGoals() {
        service.deposit(eur("30"), bike.getId(), null);
        bike.setStatus(GoalStatus.PAUSED);
        service.withdrawGoal(bike.getId(), eur("30"), false, null);

        assertEquals(0, bike.getAssignedAmount().signum());
        assertEquals(GoalStatus.PAUSED, bike.getStatus());
    }

    @Test
    void goalCanOnlyBeWithdrawnCompletely() {
        service.deposit(eur("80"), vacation.getId(), null);
        int before = allBookings().size();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.withdrawGoal(vacation.getId(), eur("50"), true, null));
        assertTrue(ex.getMessage().startsWith("Ein Sparziel wird immer komplett ausgebucht."), ex.getMessage());
        assertTrue(ex.getMessage().contains("80,00\u00a0€"), ex.getMessage());
        assertEquals(eur("80"), vacation.getAssignedAmount());
        assertEquals(GoalStatus.ACTIVE, vacation.getStatus());
        assertEquals(before, allBookings().size());

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> service.withdrawGoal(bike.getId(), eur("1"), false, null));
        assertEquals("Im Sparziel „" + bike.getName() + "“ ist kein Geld mehr.", empty.getMessage());
    }

    private List<Booking> allBookings() {
        return bookings.findByAccountIdOrderByBookedAtDescIdDesc(account.getId());
    }

    private static BigDecimal eur(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
