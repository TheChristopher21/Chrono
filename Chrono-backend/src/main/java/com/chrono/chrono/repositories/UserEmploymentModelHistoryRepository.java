package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserEmploymentModelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserEmploymentModelHistoryRepository extends JpaRepository<UserEmploymentModelHistory, Long> {
    Optional<UserEmploymentModelHistory> findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(User user, LocalDate date);

    Optional<UserEmploymentModelHistory> findFirstByUserOrderByEffectiveFromDesc(User user);

    Optional<UserEmploymentModelHistory> findFirstByUserOrderByEffectiveFromAsc(User user);

    List<UserEmploymentModelHistory> findByUserOrderByEffectiveFromAsc(User user);

    @Modifying
    @Query("delete from UserEmploymentModelHistory history "
            + "where history.user = :user and history.effectiveFrom >= :effectiveFrom")
    int deleteFromDate(@Param("user") User user, @Param("effectiveFrom") LocalDate effectiveFrom);
}
