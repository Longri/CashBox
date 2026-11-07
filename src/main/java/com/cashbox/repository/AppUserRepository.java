package com.cashbox.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashbox.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
