package com.cashbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private List<Booking> allBookings() {
        return bookings.findByAccountIdOrderByBookedAtDescIdDesc(account.getId());
    }

    private static BigDecimal eur(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
