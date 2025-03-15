package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    List<TimeTracking> findByUserOrderByStartTimeDesc(User user);

    List<TimeTracking> findByUserOrderByStartTimeAsc(User user);

    Optional<TimeTracking> findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(User user);

    Optional<TimeTracking> findTopByUserOrderByIdDesc(User user);

    Optional<TimeTracking> findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(User user, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM TimeTracking t JOIN FETCH t.user")
    List<TimeTracking> findAllWithUser();

    List<TimeTracking> findByUserAndStartTimeBetween(User user, LocalDateTime start, LocalDateTime end);

    // Methode, um den täglichen Notiz-Eintrag (punchOrder 0) anhand des reinen Datums zu finden
    List<TimeTracking> findByUserAndDailyDateAndPunchOrder(User user, LocalDate dailyDate, Integer punchOrder);

    default List<TimeTracking> findDailyNoteByUserAndDate(User user, LocalDate dailyDate) {
        return findByUserAndDailyDateAndPunchOrder(user, dailyDate, 0);
    }
}
