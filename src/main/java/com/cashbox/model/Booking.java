package com.cashbox.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Unveränderlicher Eintrag in der Buchungshistorie (Tabelle {@code booking}).
 * {@code sourceGoal == null} bzw. {@code targetGoal == null} steht für den Puffer.
 */
@Entity
@Immutable
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_goal_id")
    private SavingsGoal sourceGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_goal_id")
    private SavingsGoal targetGoal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingType type;

    @Column(length = 255)
    private String note;

    protected Booking() {
    }

    public Booking(Account account, BookingType type, BigDecimal amount,
                   SavingsGoal sourceGoal, SavingsGoal targetGoal, String note, Instant bookedAt) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.sourceGoal = sourceGoal;
        this.targetGoal = targetGoal;
        this.note = note;
        this.bookedAt = bookedAt;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public Instant getBookedAt() { return bookedAt; }
    public BigDecimal getAmount() { return amount; }
    public SavingsGoal getSourceGoal() { return sourceGoal; }
    public SavingsGoal getTargetGoal() { return targetGoal; }
    public BookingType getType() { return type; }
    public String getNote() { return note; }
}
