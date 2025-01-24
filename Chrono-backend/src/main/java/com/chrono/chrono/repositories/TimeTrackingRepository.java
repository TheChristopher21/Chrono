package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {
    Optional<TimeTracking> findFirstByUserIdOrderByCheckInTimeDesc(Long userId);

    List<TimeTracking> findAllByUserIdAndCheckInTimeAfter(Long userId, LocalDateTime checkInTime);
}
