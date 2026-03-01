package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayslipAudit;
import com.chrono.chrono.entities.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipAuditRepository extends JpaRepository<PayslipAudit, Long> {
    void deleteByPayslip(Payslip payslip);
}
