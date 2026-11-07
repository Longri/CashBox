package com.cashbox.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashbox.model.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** CashBox verwaltet genau ein reales Sparkonto. */
    Optional<Account> findFirstByOrderByIdAsc();
}
