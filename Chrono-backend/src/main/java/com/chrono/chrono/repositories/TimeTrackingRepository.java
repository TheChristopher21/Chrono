package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    Optional<TimeTracking> findByUserAndDailyDate(User user, LocalDate day);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<TimeTracking> findFirstByUserAndDailyDate(User user, LocalDate dailyDate);

    List<TimeTracking> findByUserOrderByDailyDateDesc(User user);

    List<TimeTracking> findByUserAndDailyDateBetweenOrderByDailyDateAsc(
            User user, LocalDate from, LocalDate to);

    List<TimeTracking> findByUser(User user);

    // NEUE METHODE für autoPunchOutForgottenEntries
    List<TimeTracking> findByDailyDateAndWorkStartIsNotNullAndWorkEndIsNull(LocalDate date);
}