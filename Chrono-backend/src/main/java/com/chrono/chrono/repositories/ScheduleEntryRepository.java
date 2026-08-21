package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findByDateBetween(LocalDate start, LocalDate end);
    List<ScheduleEntry> findByUser_Company_IdAndDateBetween(Long companyId, LocalDate start, LocalDate end);

    // Verhindert doppelte Eintraege pro Nutzer, Datum und Schicht
    Optional<ScheduleEntry> findByUserAndDateAndShift(User user, LocalDate date, String shift);

    List<ScheduleEntry> findAllByUserAndDate(User user, LocalDate date);

    List<ScheduleEntry> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

    void deleteByUser(User user);
}
