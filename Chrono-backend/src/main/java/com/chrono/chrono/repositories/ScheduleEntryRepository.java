package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findByDateBetween(LocalDate start, LocalDate end);

    // --- NEU: Methode zur Prüfung auf doppelte Einträge ---
    Optional<ScheduleEntry> findByUserAndDateAndShift(User user, LocalDate date, String shift);
}