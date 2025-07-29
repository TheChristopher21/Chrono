package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findByDateBetween(LocalDate start, LocalDate end);
    List<ScheduleEntry> findByDate(LocalDate date);
}
