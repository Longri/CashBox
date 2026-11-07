package com.cashbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashbox.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByAccountIdOrderByBookedAtDescIdDesc(Long accountId);
}
