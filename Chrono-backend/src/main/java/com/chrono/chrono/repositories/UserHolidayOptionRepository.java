package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserHolidayOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserHolidayOptionRepository extends JpaRepository<UserHolidayOption, Long> {
    Optional<UserHolidayOption> findByUserAndHolidayDate(User user, LocalDate holidayDate);
    List<UserHolidayOption> findByUserAndHolidayDateBetween(User user, LocalDate startDate, LocalDate endDate);
}