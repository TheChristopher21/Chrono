package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {
    List<TimeTracking> findByUserId(Long userId);
}
