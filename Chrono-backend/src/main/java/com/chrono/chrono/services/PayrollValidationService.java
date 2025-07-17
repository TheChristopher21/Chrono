package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.repositories.PayslipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PayrollValidationService {
    @Autowired
    private PayslipRepository payslipRepository;

    @Scheduled(cron = "0 0 2 * * *")
    public void validatePayslips() {
        payslipRepository.findAll().forEach(ps -> {
            if (ps.getNetSalary() < 0) {
                System.err.println("Invalid payslip " + ps.getId());
            }
        });
    }
}
