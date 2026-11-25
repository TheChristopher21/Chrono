package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByCorrectionBatch(String batch);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payslip p where p.user = :user and p.approved = true order by p.id")
    List<Payslip> findApprovedForUpdate(@Param("user") User user);
    List<Payslip> findByUser(User user);
    List<Payslip> findByUserAndApproved(User user, boolean approved);
    List<Payslip> findByApproved(boolean approved);
    List<Payslip> findByUser_Company_Id(Long companyId);
    List<Payslip> findByUser_Company_IdAndApproved(Long companyId, boolean approved);
    List<Payslip> findByUserAndPeriodStartAndPeriodEnd(User user, java.time.LocalDate start, java.time.LocalDate end);
}
