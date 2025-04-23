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

    List<TimeTracking> findByEndTimeIsNullAndStartTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM TimeTracking t WHERE t.user = :user AND t.punchOrder = :punchOrder ORDER BY t.startTime DESC")
    Optional<TimeTracking> findTopByUserAndPunchOrder(@Param("user") User user, @Param("punchOrder") Integer punchOrder);

    List<TimeTracking> findByUserAndDailyDateAndPunchOrder(User user, LocalDate dailyDate, Integer punchOrder);
    @Query(value = "SELECT DISTINCT DATE(start_time) FROM time_tracking WHERE user_id = :userId", nativeQuery = true)
    List<String> findAllTrackedDateStringsByUser(@Param("userId") Long userId);



    default List<TimeTracking> findDailyNoteByUserAndDate(User user, LocalDate dailyDate) {
        return findByUserAndDailyDateAndPunchOrder(user, dailyDate, 0);
    }
}