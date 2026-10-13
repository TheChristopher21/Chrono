package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleChangeLogRepository extends JpaRepository<ScheduleChangeLog, Long> {
    List<ScheduleChangeLog> findByScheduleDateBetweenOrderByCreatedAtDesc(LocalDate start, LocalDate end);

    List<ScheduleChangeLog> findByCompany_IdAndScheduleDateBetweenOrderByCreatedAtDesc(Long companyId, LocalDate start, LocalDate end);
}
