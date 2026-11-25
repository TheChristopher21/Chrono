package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
    List<AnalyticsEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime start);
}
