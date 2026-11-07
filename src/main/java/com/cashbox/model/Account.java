package com.cashbox.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Das reale Sparkonto (Tabelle {@code account}). Es gilt immer:
 * {@code currentBalance = unassignedBuffer + Summe(assignedAmount aller Sparziele)}.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "unassigned_buffer", nullable = false, precision = 19, scale = 2)
    private BigDecimal unassignedBuffer = BigDecimal.ZERO;

    @Version
    private Long version;

    protected Account() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getUnassignedBuffer() { return unassignedBuffer; }
    public Long getVersion() { return version; }

    /** Geld kommt von außen auf das Konto und landet im Puffer. */
    public void deposit(BigDecimal amount) {
        currentBalance = currentBalance.add(amount);
        unassignedBuffer = unassignedBuffer.add(amount);
    }

    public void addToBuffer(BigDecimal amount) {
        unassignedBuffer = unassignedBuffer.add(amount);
    }

    public void takeFromBuffer(BigDecimal amount) {
        if (unassignedBuffer.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Im Puffer ist nicht genug Geld.");
        }
        unassignedBuffer = unassignedBuffer.subtract(amount);
    }
}
