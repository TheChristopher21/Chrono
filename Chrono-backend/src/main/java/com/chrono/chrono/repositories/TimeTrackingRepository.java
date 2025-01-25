package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    // Find the latest time tracking entry for a specific user
    Optional<TimeTracking> findFirstByUserOrderByCheckInDesc(User user);

    // Find all time tracking entries for a user within a specific time range
    @Query("SELECT t FROM TimeTracking t WHERE t.user.id = :userId AND t.checkIn >= :checkInTime")
    List<TimeTracking> findAllByUserIdAndCheckInTimeAfter(@Param("userId") Long userId, @Param("checkInTime") LocalDateTime checkInTime);
}
