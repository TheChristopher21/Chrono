package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.PayslipAudit;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.PayslipAuditRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PayrollService {
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;
    @Autowired
    private PayslipAuditRepository payslipAuditRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PdfService pdfService;

    private static final double TAX_RATE = 0.15; // simple example tax
    private static final double SOCIAL_RATE = 0.05; // social deductions
    private static final double OVERTIME_BONUS = 0.25;

    private void audit(Payslip ps, String action, String author, String comment) {
        PayslipAudit log = new PayslipAudit(ps, action, author, comment);
        payslipAuditRepository.save(log);
    }

    @Transactional
    public Payslip generatePayslip(Long userId, LocalDate start, LocalDate end) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(user, startDt, endDt);
        long minutes = 0;
        LocalDateTime lastStart = null;
        for (TimeTrackingEntry e : entries) {
            if (e.getPunchType() == TimeTrackingEntry.PunchType.START) {
                lastStart = e.getEntryTimestamp();
            } else if (e.getPunchType() == TimeTrackingEntry.PunchType.ENDE && lastStart != null) {
                minutes += Duration.between(lastStart, e.getEntryTimestamp()).toMinutes();
                lastStart = null;
            }
        }
        double hours = minutes / 60.0;
        double rate = user.getHourlyRate() != null ? user.getHourlyRate() : 0.0;
        double overtimeHours = Math.max(0, hours - 160);
        double baseHours = hours - overtimeHours;
        double gross = baseHours * rate + overtimeHours * rate * (1 + OVERTIME_BONUS);
        double deductions = gross * (TAX_RATE + SOCIAL_RATE);
        double net = gross - deductions;
        Payslip ps = new Payslip();
        ps.setUser(user);
        ps.setPeriodStart(start);
        ps.setPeriodEnd(end);
        Integer maxVersion = payslipRepository.findByUserAndPeriodStartAndPeriodEnd(user, start, end)
                .stream().map(Payslip::getVersion).max(Integer::compareTo).orElse(0);
        ps.setVersion(maxVersion == null ? 0 : maxVersion + 1);
        ps.setGrossSalary(gross);
        ps.setDeductions(deductions);
        ps.setNetSalary(net);
        ps.setAllowances(0.0);
        ps.setBonuses(0.0);
        ps.setOneTimePayments(0.0);
        ps.setTaxFreeAllowances(0.0);
        ps.setBankAccount(user.getBankAccount());
        ps.setSocialSecurityNumber(user.getSocialSecurityNumber());
        ps.setPayType(user.getIsHourly() != null && user.getIsHourly() ? "hourly" : "salary");
        ps.setApproved(false);
        Payslip saved = payslipRepository.save(ps);
        audit(saved, "GENERATED", user.getUsername(), null);
        emailService.sendPayslipGeneratedMail(user, saved);
        return saved;
    }

    public List<Payslip> getPayslipsForUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return payslipRepository.findByUserAndApproved(user, true);
    }

    @Transactional
    public void approvePayslip(Long id, String comment) {
        Payslip ps = payslipRepository.findById(id).orElseThrow();
        ps.setApproved(true);
        ps.setLocked(true);
        String pdf = pdfService.generatePayslipPdf(ps);
        ps.setPdfPath(pdf);
        payslipRepository.save(ps);
        audit(ps, "APPROVED", "ADMIN", comment);
        emailService.sendPayslipApprovedMail(ps.getUser(), ps);
    }

    @Transactional
    public void approveAllForUser(Long userId, String comment) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Payslip> slips = payslipRepository.findByUser(user);
        for (Payslip ps : slips) {
            ps.setApproved(true);
            ps.setLocked(true);
            String pdf = pdfService.generatePayslipPdf(ps);
            ps.setPdfPath(pdf);
            audit(ps, "APPROVED", "ADMIN", comment);
            emailService.sendPayslipApprovedMail(ps.getUser(), ps);
        }
        payslipRepository.saveAll(slips);
    }

    @Transactional
    public void approveAll(String comment) {
        List<Payslip> slips = payslipRepository.findAll();
        for (Payslip ps : slips) {
            ps.setApproved(true);
            ps.setLocked(true);
            String pdf = pdfService.generatePayslipPdf(ps);
            ps.setPdfPath(pdf);
            audit(ps, "APPROVED", "ADMIN", comment);
            emailService.sendPayslipApprovedMail(ps.getUser(), ps);
        }
        payslipRepository.saveAll(slips);
    }

    public List<Payslip> getAllPayslips() {
        return payslipRepository.findAll();
    }

    public List<Payslip> getPendingPayslips() {
        return payslipRepository.findByApproved(false);
    }

    @Transactional(readOnly = true)
    public List<PayslipDTO> getApprovedPayslips() {
        return payslipRepository.findByApproved(true)
                .stream().map(PayslipDTO::new).toList();
    }
}
