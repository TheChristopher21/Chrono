package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByUser(User user);
    List<Payslip> findByUserAndApproved(User user, boolean approved);
    List<Payslip> findByApproved(boolean approved);
    List<Payslip> findByUserAndPeriodStartAndPeriodEnd(User user, java.time.LocalDate start, java.time.LocalDate end);
}
