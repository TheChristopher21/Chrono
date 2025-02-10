package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    List<TimeTracking> findByUserOrderByStartTimeDesc(User user);

    List<TimeTracking> findByUserOrderByStartTimeAsc(User user);

    Optional<TimeTracking> findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(User user);

    Optional<TimeTracking> findTopByUserOrderByIdDesc(User user);

    // Liefert den letzten abgeschlossenen Eintrag (mit endTime) im angegebenen Zeitraum
    Optional<TimeTracking> findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(User user, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM TimeTracking t JOIN FETCH t.user")
    List<TimeTracking> findAllWithUser();

    // Neue Methode: Finde alle Einträge eines Nutzers, deren Startzeit innerhalb des angegebenen Zeitraums liegt
    List<TimeTracking> findByUserAndStartTimeBetween(User user, LocalDateTime start, LocalDateTime end);
}
