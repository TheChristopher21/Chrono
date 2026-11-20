package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, Long> {
    List<ScheduleRule> findByCompanyIsNullOrderByStartTime();
    List<ScheduleRule> findByCompany_IdOrderByStartTime(Long companyId);
    Optional<ScheduleRule> findByCompanyIsNullAndShiftKey(String shiftKey);
    Optional<ScheduleRule> findByCompany_IdAndShiftKey(Long companyId, String shiftKey);
}
