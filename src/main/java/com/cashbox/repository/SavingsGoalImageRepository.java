package com.cashbox.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cashbox.model.SavingsGoalImage;

public interface SavingsGoalImageRepository extends JpaRepository<SavingsGoalImage, Long> {

    /** Nur das angezeigte Bild – ohne das deutlich größere Quellbild zu laden. */
    interface Thumbnail {
        String getContentType();

        byte[] getData();
    }

    /** IDs der Sparziele des Kontos, die ein eigenes Bild haben – ohne die Bilddaten zu laden. */
    @Query("select i.goalId from SavingsGoalImage i where i.goalId in "
            + "(select g.id from SavingsGoal g where g.account.id = :accountId)")
    Set<Long> findGoalIdsWithImage(@Param("accountId") Long accountId);

    @Query("select i.contentType as contentType, i.data as data from SavingsGoalImage i where i.goalId = :goalId")
    Optional<Thumbnail> findThumbnail(@Param("goalId") Long goalId);
}
