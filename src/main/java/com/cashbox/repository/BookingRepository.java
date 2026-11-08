package com.cashbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cashbox.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByAccountIdOrderByBookedAtDescIdDesc(Long accountId);

    /** Gibt es mindestens eine Buchung, die das Sparziel als Quelle oder Ziel hat? */
    // Explizite LEFT JOINs: ein impliziter (innerer) Join würde Buchungen ohne Quelle/Ziel wegfiltern.
    @Query("""
            select count(b) > 0 from Booking b
              left join b.sourceGoal s
              left join b.targetGoal t
             where s.id = :goalId or t.id = :goalId""")
    boolean existsForGoal(@Param("goalId") Long goalId);
}
