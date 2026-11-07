package com.cashbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashbox.model.SavingsGoal;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByAccountId(Long accountId);
}
