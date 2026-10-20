package com.chrono.chrono.services;

import com.chrono.chrono.security.CompanyScope;
import com.chrono.chrono.dto.PayslipDTO;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.PayslipAudit;
import com.chrono.chrono.entities.PayComponent;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.PayslipSchedule;
import com.chrono.chrono.repositories.PayslipAuditRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.PayslipScheduleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.PdfService;
import com.chrono.chrono.services.TaxCalculationService;
import com.chrono.chrono.services.accounting.AccountingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PayrollService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PayrollService.class);
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PayslipAuditRepository payslipAuditRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PdfService pdfService;
    @Autowired
    private PayslipScheduleRepository payslipScheduleRepository;

    @Autowired
    private AccountingService accountingService;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private PayrollApprovalGuard approvalGuard;

    @Autowired
    private PayrollCalculationService calculations;

    private void audit(Payslip ps, String action, String author, String comment) {
        PayslipAudit log = new PayslipAudit(ps, action, author, comment);
        payslipAuditRepository.save(log);
    }

    private void afterCommit(Runnable notification) {
        Runnable delivery = () -> {
            try { notification.run(); }
            catch (RuntimeException failure) {
                // Payroll is already committed; an HTTP failure would invite a duplicate retry.
                logger.error("Abrechnung gespeichert; E-Mail-Benachrichtigung konnte nicht zugestellt werden.", failure);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delivery.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { delivery.run(); }
        });
    }

    @Transactional
    public Payslip generatePayslip(Long userId, LocalDate start, LocalDate end, LocalDate payoutDate) {
        return generatePayslip(userId, start, end, payoutDate, null, false);
    }

    @Transactional
    public Payslip generatePayslip(Long userId, LocalDate start, LocalDate end, LocalDate payoutDate, Double overtimeHours, boolean payoutOvertime) {
        PayrollReviewService.validatePeriod(start, end);
        if (payoutOvertime && (overtimeHours == null || !Double.isFinite(overtimeHours) || overtimeHours <= 0)) {
            throw new IllegalArgumentException("Bitte eine positive, endliche Anzahl Überstunden angeben.");
        }
        User user = userRepository.findById(userId).orElseThrow();
        var calculation = calculations.calculate(user, start, end, overtimeHours, payoutOvertime);
        double basePay = calculation.gross().base().doubleValue();
        double overtimePay = calculation.gross().premium().doubleValue();
        double manualOvertimePay = calculation.gross().payout().doubleValue();
        double paidOvertimeHours = calculation.gross().paidOvertimeMinutes() / 60.0;
        double gross = calculation.gross().total().doubleValue();
        TaxCalculationService.Result res = new TaxCalculationService.Result(calculation.employee(), calculation.employer(), calculation.taxReference());
        double deductions = res.getEmployeeTotal();
        double net = roundMoney(gross - deductions);
        double employer = res.getEmployerTotal();
        Payslip ps = new Payslip();
        ps.setUser(user);
        ps.setPeriodStart(start);
        ps.setPeriodEnd(end);
        // JPA @Version is the optimistic-lock token, not a payroll revision counter.
        ps.setCurrency(calculation.terms().currency());
        ps.setCalculationVersion(PayrollCalculationService.VERSION);
        ps.setCalculationSnapshot(calculations.snapshot(calculation));
        ps.setGrossSalary(gross);
        ps.setDeductions(deductions);
        ps.setNetSalary(net);
        ps.setAllowances(0.0);
        ps.setBonuses(0.0);
        ps.setOneTimePayments(0.0);
        ps.setTaxFreeAllowances(0.0);
        ps.getEarnings().add(new PayComponent("Base salary", basePay));
        if (calculation.gross().thirteenthAmount().signum() > 0) ps.getEarnings().add(new PayComponent("Thirteenth salary", calculation.gross().thirteenthAmount().doubleValue()));
        if (overtimePay > 0) {
            ps.getEarnings().add(new PayComponent("Overtime premium", overtimePay));
        }
        if (payoutOvertime && paidOvertimeHours > 0) {
            ps.getEarnings().add(new PayComponent("Overtime payout", manualOvertimePay));
            ps.setOvertimeHours(paidOvertimeHours);
            ps.setPayoutOvertime(true);
        }
        for (Map.Entry<String, Double> entry : res.getEmployee().entrySet()) {
            ps.getDeductionsList().add(new PayComponent(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Double> entry : res.getEmployer().entrySet()) {
            ps.getEmployerContribList().add(new PayComponent(entry.getKey(), entry.getValue()));
        }
        ps.setEmployerContributions(employer);
        if (payoutDate != null && calculation.gross().thirteenthAmount().signum() > 0 && !java.time.YearMonth.from(payoutDate).equals(java.time.YearMonth.from(end)))
            throw new IllegalArgumentException("Der 13. Monatslohn muss im Abrechnungsmonat ausbezahlt werden. Spätere Nachzahlungen als Rückrechnung zuordnen.");
        if (payoutDate != null) {
            ps.setPayoutDate(payoutDate);
        } else {
            ps.setPayoutDate(calculation.gross().thirteenthAmount().signum() > 0 ? end : end.plusDays(5));
        }
        ps.setBankAccount(user.getBankAccount());
        ps.setSocialSecurityNumber(user.getSocialSecurityNumber());
        ps.setPayType(calculation.terms().hourly() ? "hourly" : "salary");
        ps.setApproved(false);
        PayrollApprovalGuard.validateAmounts(ps);
        Payslip saved = payslipRepository.save(ps);
        audit(saved, "GENERATED", user.getUsername(), PayrollCalculationService.VERSION + "; Regeln #" + calculation.rulesVersion());
        afterCommit(() -> emailService.sendPayslipGeneratedMail(user, saved));
        return saved;
    }

    private static double roundMoney(double amount) {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Ungültiger Lohnbetrag.");
        return java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    @Transactional(readOnly = true)
    public List<Payslip> getPayslipsForUser(Long userId, LocalDate start, LocalDate end) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Payslip> list = payslipRepository.findByUserAndApproved(user, true);

        if (start != null) {
            list = list.stream().filter(ps -> !ps.getPeriodEnd().isBefore(start)).toList();
        }
        if (end != null) {
            list = list.stream().filter(ps -> !ps.getPeriodStart().isAfter(end)).toList();
        }

        return list;
    }

    @Transactional
    public void approveBatch(List<Long> ids, String comment) {
        if (ids == null || ids.isEmpty() || ids.size() > 500 || ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Bitte zwischen 1 und 500 Abrechnungen auswählen.");
        }
        List<Long> uniqueIds = ids.stream().distinct().sorted().toList();
        List<Payslip> batch = payslipRepository.findAllById(uniqueIds);
        if (batch.size() != uniqueIds.size()) throw new IllegalArgumentException("Mindestens eine Abrechnung wurde nicht gefunden.");
        approvalGuard.validate(batch);
        for (Payslip slip : batch.stream().sorted(java.util.Comparator.comparing(Payslip::getPeriodStart)).toList()) approveValidated(slip, comment);
    }

    @Transactional
    public void approveForUsers(List<Long> userIds, String comment) {
        List<Payslip> batch = userIds.stream().distinct().sorted()
                .flatMap(id -> payslipRepository.findByUser(userRepository.findById(id).orElseThrow()).stream())
                .filter(slip -> !slip.isApproved()).toList();
        approvalGuard.validate(batch);
        for (Payslip slip : batch.stream().sorted(java.util.Comparator.comparing(Payslip::getPeriodStart)).toList()) approveValidated(slip, comment);
    }

    @Transactional
    public void approvePayslip(Long id, String comment) {
        Payslip ps = payslipRepository.findById(id).orElseThrow();
        approvalGuard.validate(List.of(ps));
        approveValidated(ps, comment);
    }
    private void approveValidated(Payslip ps, String comment) {
        if (ps.isApproved()) {
            timeTrackingService.rebuildUserBalance(ps.getUser());
            return;
        }
        ps.setApproved(true);
        ps.setLocked(true);
        String pdf = pdfService.generatePayslipPdf(ps);
        ps.setPdfPath(pdf);
        payslipRepository.saveAndFlush(ps);
        timeTrackingService.rebuildUserBalance(ps.getUser());
        accountingService.recordPayrollPosting(ps);
        audit(ps, "APPROVED", "ADMIN", comment);
        afterCommit(() -> emailService.sendPayslipApprovedMail(ps.getUser(), ps));
    }

    @Transactional
    public void approveAllForUser(Long userId, String comment) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Payslip> slips = payslipRepository.findByUser(user).stream()
                .filter(ps -> !ps.isApproved())
                .toList();
        approvalGuard.validate(slips);
        slips = slips.stream().filter(ps -> !ps.isApproved()).toList();
        for (Payslip ps : slips) {
            ps.setApproved(true);
            ps.setLocked(true);
            String pdf = pdfService.generatePayslipPdf(ps);
            ps.setPdfPath(pdf);
            accountingService.recordPayrollPosting(ps);
            audit(ps, "APPROVED", "ADMIN", comment);
            afterCommit(() -> emailService.sendPayslipApprovedMail(ps.getUser(), ps));
        }
        if (!slips.isEmpty()) {
            payslipRepository.saveAllAndFlush(slips);
        }
        timeTrackingService.rebuildUserBalance(user);
    }

    @Transactional
    public void approveAll(String comment) {
        List<Payslip> slips = payslipRepository.findAll().stream()
                .filter(ps -> !ps.isApproved())
                .toList();
        approvalGuard.validate(slips);
        slips = slips.stream().filter(ps -> !ps.isApproved()).toList();
        for (Payslip ps : slips) {
            ps.setApproved(true);
            ps.setLocked(true);
            String pdf = pdfService.generatePayslipPdf(ps);
            ps.setPdfPath(pdf);
            audit(ps, "APPROVED", "ADMIN", comment);
            accountingService.recordPayrollPosting(ps);
            afterCommit(() -> emailService.sendPayslipApprovedMail(ps.getUser(), ps));
        }
        if (!slips.isEmpty()) {
            payslipRepository.saveAllAndFlush(slips);
        }

        Set<Long> rebuiltUserIds = new HashSet<>();
        slips.stream()
                .map(Payslip::getUser)
                .filter(user -> user != null && rebuiltUserIds.add(user.getId()))
                .forEach(timeTrackingService::rebuildUserBalance);
    }

    @Transactional
    public void deletePayslip(Long id) {
        Payslip ps = payslipRepository.findById(id).orElseThrow();
        if (ps.isApproved()) {
            throw new IllegalStateException("Cannot delete approved payslip");
        }
        if (ps.isCorrection()) throw new IllegalStateException("Korrekturentwürfe müssen als vollständiger Lauf verworfen werden.");
        payslipAuditRepository.deleteByPayslip(ps);
        payslipRepository.delete(ps);
    }

    @Transactional
    public void reopenPayslip(Long id) {
        Payslip ps = payslipRepository.findById(id).orElseThrow();
        if (ps.isApproved()) throw new IllegalStateException("Freigegebene Abrechnungen bleiben unverändert. Eine Korrektur benötigt eine separate Storno-/Korrekturbuchung.");
        ps.setApproved(false);
        ps.setLocked(false);
        ps.setPdfPath(null);
        payslipRepository.saveAndFlush(ps);
        timeTrackingService.rebuildUserBalance(ps.getUser());
        audit(ps, "REOPENED", "ADMIN", null);
    }

    public List<Payslip> getAllPayslips() {
        return payslipRepository.findAll();
    }

    public List<Payslip> getAllPayslips(User requestingAdmin) {
        boolean isSuperAdmin = requestingAdmin.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (isSuperAdmin && CompanyScope.companyFor(requestingAdmin) == null) {
            return payslipRepository.findAll();
        }
        if (CompanyScope.companyFor(requestingAdmin) == null) {
            return List.of();
        }
        return payslipRepository.findByUser_Company_Id(CompanyScope.companyFor(requestingAdmin).getId());
    }

    public List<Payslip> getPendingPayslips(User requestingAdmin) {
        boolean isSuperAdmin = requestingAdmin.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (isSuperAdmin && CompanyScope.companyFor(requestingAdmin) == null) {
            return payslipRepository.findByApproved(false);
        }

        if (CompanyScope.companyFor(requestingAdmin) == null) {
            return List.of();
        }

        return payslipRepository.findByUser_Company_IdAndApproved(
                CompanyScope.companyFor(requestingAdmin).getId(), false);
    }

    @Transactional(readOnly = true)
    public List<PayslipDTO> getApprovedPayslips(User requestingAdmin, String name, LocalDate start, LocalDate end) {
        boolean isSuperAdmin = requestingAdmin.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

        List<Payslip> list;
        if (isSuperAdmin && CompanyScope.companyFor(requestingAdmin) == null) {
            list = payslipRepository.findByApproved(true);
        } else if (CompanyScope.companyFor(requestingAdmin) != null) {
            list = payslipRepository.findByUser_Company_IdAndApproved(
                    CompanyScope.companyFor(requestingAdmin).getId(), true);
        } else {
            list = List.of();
        }
        if (name != null && !name.isBlank()) {
            String lower = name.toLowerCase();
            list = list.stream().filter(ps -> {
                User u = ps.getUser();
                if (u == null) return false;
                String full = (u.getFirstName() + " " + u.getLastName()).toLowerCase();
                return full.contains(lower) ||
                        (u.getUsername() != null && u.getUsername().toLowerCase().contains(lower));
            }).toList();
        }
        if (start != null) {
            list = list.stream().filter(ps -> !ps.getPeriodEnd().isBefore(start)).toList();
        }
        if (end != null) {
            list = list.stream().filter(ps -> !ps.getPeriodStart().isAfter(end)).toList();
        }
        return list.stream().map(PayslipDTO::new).toList();

    }


    @Transactional
    public byte[] getPayslipPdf(Long id) {
        return getPayslipPdf(id, "de");
    }

    @Transactional
    public byte[] getPayslipPdf(Long id, String lang) {
        Payslip ps = payslipRepository.findById(id).orElseThrow();
        String path = ps.getPdfPath();
        if (path != null && "de".equalsIgnoreCase(lang)) {
            try {
                return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
            } catch (java.io.IOException ignore) {
                // fall through to regeneration
            }
        }

        byte[] bytes = pdfService.generatePayslipPdfBytes(ps, lang);
        if (bytes == null) {
            return null;
        }

        if ("de".equalsIgnoreCase(lang)) {
            try {
                path = "/tmp/payslip-" + ps.getId() + ".pdf";
                java.nio.file.Files.write(java.nio.file.Path.of(path), bytes);
                ps.setPdfPath(path);
                payslipRepository.save(ps);
            } catch (java.io.IOException ignore) {
                // ignore write error, still return bytes
            }
        }

        return bytes;


    }

    @Transactional
    public void setPayoutDate(Long payslipId, LocalDate payoutDate) {
        Payslip ps = payslipRepository.findById(payslipId).orElseThrow();
        if (ps.isApproved()) throw new IllegalStateException("Das Auszahlungsdatum einer freigegebenen Abrechnung darf nicht nachträglich verändert werden.");
        if (!ps.isCorrection() && ps.getEarnings().stream().anyMatch(c -> "Thirteenth salary".equals(c.getType()))
                && (payoutDate == null || !java.time.YearMonth.from(payoutDate).equals(java.time.YearMonth.from(ps.getPeriodEnd()))))
            throw new IllegalArgumentException("Der 13. Monatslohn muss im Abrechnungsmonat ausbezahlt werden.");
        ps.setPayoutDate(payoutDate);
        payslipRepository.save(ps);
    }
    public void setPayslipSchedule(Long userId, int dayOfMonth) {
        User user = userRepository.findById(userId).orElseThrow();
        PayslipSchedule sched = payslipScheduleRepository.findByUser(user).orElse(new PayslipSchedule());
        sched.setUser(user);
        sched.setDayOfMonth(dayOfMonth);
        LocalDate today = LocalDate.now();
        LocalDate next = today.withDayOfMonth(Math.min(dayOfMonth, today.lengthOfMonth()));
        if (!next.isAfter(today)) {
            LocalDate tmp = today.plusMonths(1);
            next = tmp.withDayOfMonth(Math.min(dayOfMonth, tmp.lengthOfMonth()));
        }
        sched.setNextRun(next);
        payslipScheduleRepository.save(sched);
    }

    /**
     * Enable monthly payslip generation for all non-deleted users.
     * The day parameter indicates which day of the month should trigger creation.
     */
    @Transactional
    public void setPayslipScheduleForAll(int dayOfMonth) {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (!u.isDeleted()) {
                setPayslipSchedule(u.getId(), dayOfMonth);
            }
        }
    }

    @Transactional
    public void setPayslipScheduleForAll(User requestingAdmin, int dayOfMonth) {
        boolean isSuperAdmin = requestingAdmin.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        List<User> users;
        if (isSuperAdmin && CompanyScope.companyFor(requestingAdmin) == null) {
            users = userRepository.findOperationalUsersDeletedFalse();
        } else if (CompanyScope.companyFor(requestingAdmin) != null) {
            users = userRepository.findOperationalUsersByCompanyIdAndDeletedFalse(CompanyScope.companyFor(requestingAdmin).getId());
        } else {
            users = List.of();
        }
        for (User u : users) {
            setPayslipSchedule(u.getId(), dayOfMonth);
        }
    }
}
