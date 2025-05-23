package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    // Holt genau den Eintrag für (user, dailyDate)
    Optional<TimeTracking> findByUserAndDailyDate(User user, LocalDate dailyDate);

    // History: Liste aller Einträge für User, absteigend nach Datum
    List<TimeTracking> findByUserOrderByDailyDateDesc(User user);

    // Datensätze in Datumsspanne (z.B. für Reports)
    List<TimeTracking> findByUserAndDailyDateBetweenOrderByDailyDateAsc(
            User user, LocalDate from, LocalDate to);

    // Falls du alle Datensätze eines Users brauchst (z.B. zum Salden-Rebuild)
    List<TimeTracking> findByUser(User user);

    // Option, um Distinct-Liste zu haben (hier könnte man anders vorgehen)
    // List<LocalDate> findDistinctDailyDateByUser(User user);
}
