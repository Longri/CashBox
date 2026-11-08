package com.cashbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashbox.model.SavingsGoal;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByAccountId(Long accountId);

    /*
     * Der Vergleich läuft in der Datenbank. Mit der Kollation utf8mb4_unicode_ci gelten dort
     * Groß-/Kleinschreibung UND Akzente als gleich („Fahrrad“ = „fährrad“) – genau wie beim
     * eindeutigen Index uq_goal_name_per_account.
     */

    boolean existsByAccountIdAndNameIgnoreCase(Long accountId, String name);

    boolean existsByAccountIdAndNameIgnoreCaseAndIdNot(Long accountId, String name, Long id);
}
