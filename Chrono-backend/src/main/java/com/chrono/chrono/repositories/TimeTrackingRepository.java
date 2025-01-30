package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    // Finde den neuesten offenen Eintrag (endTime = null) für einen User
    Optional<TimeTracking> findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(User user);

    // Alle Einträge für diesen User (sortiert nach startTime absteigend?)
    List<TimeTracking> findByUserOrderByStartTimeDesc(User user);
}
