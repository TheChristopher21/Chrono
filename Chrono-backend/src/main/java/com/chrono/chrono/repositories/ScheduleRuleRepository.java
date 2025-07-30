package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, Long> {
    List<ScheduleRule> findByIsActiveTrueOrderByStartTime();
}