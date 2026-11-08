package com.cashbox.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;

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
import jakarta.persistence.Version;

/**
 * Virtueller Spartopf (Tabelle {@code savings_goal}).
 */
@Entity
@Table(name = "savings_goal")
public class SavingsGoal {

    /** Anzeigereihenfolge: Status (aktiv zuerst), dann Priorität (leer zuletzt), dann Name. */
    public static final Comparator<SavingsGoal> DISPLAY_ORDER = Comparator
            .comparing(SavingsGoal::getStatus)
            .thenComparing(SavingsGoal::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SavingsGoal::getName, String.CASE_INSENSITIVE_ORDER);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Name eines {@link GoalIcon}; {@code null} = Standardsymbol. Ein eigenes Bild steht in {@link SavingsGoalImage}. */
    @Column(length = GoalIcon.MAX_KEY_LENGTH)
    private String icon;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "assigned_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal assignedAmount = BigDecimal.ZERO;

    private Integer priority;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Version
    private Long version;

    protected SavingsGoal() {
    }

    public SavingsGoal(Account account, String name, BigDecimal targetAmount) {
        this.account = account;
        this.name = name;
        this.targetAmount = targetAmount;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    /** Das gewählte Standardsymbol oder {@code null} (Standard bzw. unbekannter Name). */
    public GoalIcon getIcon() { return GoalIcon.fromKey(icon).orElse(null); }
    public void setIcon(GoalIcon icon) { this.icon = icon == null ? null : icon.name(); }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getAssignedAmount() { return assignedAmount; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public Instant getCreatedAt() { return createdAt; }
    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }
    public Long getVersion() { return version; }

    public void assign(BigDecimal amount) {
        assignedAmount = assignedAmount.add(amount);
    }

    public void release(BigDecimal amount) {
        if (assignedAmount.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Im Sparziel „" + name + "“ ist nicht genug Geld.");
        }
        assignedAmount = assignedAmount.subtract(amount);
    }
}
