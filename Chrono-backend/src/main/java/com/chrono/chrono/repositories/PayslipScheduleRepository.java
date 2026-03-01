package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayslipSchedule;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayslipScheduleRepository extends JpaRepository<PayslipSchedule, Long> {
    Optional<PayslipSchedule> findByUser(User user);
    List<PayslipSchedule> findByNextRunLessThanEqual(LocalDate date);
}
