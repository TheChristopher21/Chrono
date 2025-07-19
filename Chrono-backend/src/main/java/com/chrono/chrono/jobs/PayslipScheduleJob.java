package com.chrono.chrono.jobs;

import com.chrono.chrono.entities.PayslipSchedule;
import com.chrono.chrono.repositories.PayslipScheduleRepository;
import com.chrono.chrono.services.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class PayslipScheduleJob {
    @Autowired
    private PayslipScheduleRepository scheduleRepository;
    @Autowired
    private PayrollService payrollService;

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void generateScheduledPayslips() {
        LocalDate today = LocalDate.now();
        List<PayslipSchedule> due = scheduleRepository.findByNextRunLessThanEqual(today);
        for (PayslipSchedule s : due) {
            LocalDate start = s.getNextRun().minusMonths(1).withDayOfMonth(1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            payrollService.generatePayslip(s.getUser().getId(), start, end);
            LocalDate next = s.getNextRun().plusMonths(1);
            next = next.withDayOfMonth(Math.min(s.getDayOfMonth(), next.lengthOfMonth()));
            s.setNextRun(next);
        }
        scheduleRepository.saveAll(due);
    }
}
