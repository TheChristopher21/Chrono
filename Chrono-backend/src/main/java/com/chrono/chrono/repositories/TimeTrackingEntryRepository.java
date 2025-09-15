package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingEntryRepository extends JpaRepository<TimeTrackingEntry, Long> {

    @Query("SELECT tte FROM TimeTrackingEntry tte WHERE tte.user = :user AND FUNCTION('DATE', tte.entryTimestamp) = :date ORDER BY tte.entryTimestamp ASC")
    List<TimeTrackingEntry> findByUserAndEntryDateOrderByEntryTimestampAsc(@Param("user") User user, @Param("date") LocalDate date);

    @Query("SELECT tte FROM TimeTrackingEntry tte WHERE tte.user = :user AND FUNCTION('DATE', tte.entryTimestamp) = :date ORDER BY tte.entryTimestamp DESC")
    List<TimeTrackingEntry> findByUserAndEntryDateOrderByEntryTimestampDesc(@Param("user") User user, @Param("date") LocalDate date);

    default Optional<TimeTrackingEntry> findLastEntryByUserAndDate(User user, LocalDate date) {
        return findByUserAndEntryDateOrderByEntryTimestampDesc(user, date).stream().findFirst();
    }

    @Query("SELECT tte FROM TimeTrackingEntry tte WHERE tte.user = :user AND tte.entryTimestamp >= :startDateTime AND tte.entryTimestamp < :endDateTime ORDER BY tte.entryTimestamp ASC")
    List<TimeTrackingEntry> findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(
            @Param("user") User user,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
    List<TimeTrackingEntry> findByUserOrderByEntryTimestampAsc(User user);

    List<TimeTrackingEntry> findByUserOrderByEntryTimestampDesc(User user);

    @Query("SELECT DISTINCT tte.user FROM TimeTrackingEntry tte " +
       "WHERE FUNCTION('DATE', tte.entryTimestamp) = :date " +
       "AND tte.id = (SELECT MAX(sub_tte.id) FROM TimeTrackingEntry sub_tte WHERE sub_tte.user = tte.user AND FUNCTION('DATE', sub_tte.entryTimestamp) = :date) " +
       "AND tte.punchType = com.chrono.chrono.entities.TimeTrackingEntry.PunchType.START")
    List<User> findUsersWithLastEntryAsStartOnDate(@Param("date") LocalDate date);
    
    void deleteByUser(User user);

    @Query("SELECT t.customer.id FROM TimeTrackingEntry t WHERE t.user.id = :userId AND t.customer IS NOT NULL GROUP BY t.customer.id ORDER BY MAX(t.entryTimestamp) DESC")
    List<Long> findRecentCustomerIds(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    List<TimeTrackingEntry> findByProjectAndEntryTimestampBetween(
            Project project,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );
}
