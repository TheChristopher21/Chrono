package com.chrono.chrono.services;

import com.chrono.chrono.entities.PayComponent;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates the whole batch before creating PDFs, postings or notifications. */
@Service
public class PayrollApprovalGuard {
    private final UserRepository users;
    private final PayslipRepository payslips;
    private final EntityManager entities;
    private final PayrollCalculationService calculations;
    private final TimeTrackingService tracking;

    public PayrollApprovalGuard(UserRepository users, PayslipRepository payslips, EntityManager entities, PayrollCalculationService calculations, TimeTrackingService tracking) {
        this.users = users;
        this.payslips = payslips;
        this.entities = entities;
        this.calculations = calculations; this.tracking = tracking;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validate(List<Payslip> batch) {
        if (batch == null || batch.stream().anyMatch(slip -> slip == null || slip.getId() == null
                || slip.getUser() == null || slip.getUser().getId() == null)) {
            throw new IllegalStateException("Eine Abrechnung ist unvollständig zugeordnet.");
        }
        Map<Long, User> lockedUsers = new LinkedHashMap<>();
        batch.stream().map(Payslip::getUser).filter(Objects::nonNull).map(User::getId).distinct().sorted()
                .forEach(id -> {
                    User locked = users.findByIdForUpdate(id).orElseThrow();
                    entities.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
                    lockedUsers.put(id, locked);
                });
        // A controller may already have loaded the entity before waiting for the employee lock.
        batch.stream().sorted(Comparator.comparing(Payslip::getId)).forEach(slip -> entities.refresh(slip, LockModeType.PESSIMISTIC_WRITE));
        for (String group : batch.stream().filter(s -> !s.isApproved() && s.isCorrection()).map(Payslip::getCorrectionBatch).distinct().toList()) {
            if (group == null) throw new IllegalStateException("Korrekturlauf-Zuordnung fehlt.");
            var members = payslips.findByCorrectionBatch(group);
            if (members.isEmpty() || members.stream().anyMatch(s -> s.isApproved() || batch.stream().noneMatch(b -> b.getId().equals(s.getId()))))
                throw new IllegalStateException("Korrekturläufe müssen vollständig und gemeinsam freigegeben werden.");
            if (members.stream().map(Payslip::getCorrectionOfId).distinct().count() != members.size()) throw new IllegalStateException("Doppelte Originalzuordnung im Korrekturlauf.");
            var first = members.stream().min(Comparator.comparing(Payslip::getPeriodStart)).orElseThrow();
            var affected = payslips.findByUserAndApproved(first.getUser(), true).stream().filter(s -> !s.isCorrection()
                    && s.getPeriodStart().getYear() == first.getPeriodStart().getYear() && !s.getPeriodStart().isBefore(first.getPeriodStart())).map(Payslip::getId).sorted().toList();
            if (!affected.equals(members.stream().map(Payslip::getCorrectionOfId).sorted().toList())) throw new IllegalStateException("Weitere Folgemonate wurden freigegeben. Korrekturlauf neu erstellen.");
        }
        for (User user : lockedUsers.values()) {
            if (batch.stream().anyMatch(s -> !s.isApproved() && s.isPayoutOvertime() && Objects.equals(s.getUser().getId(), user.getId()))) tracking.rebuildUserBalance(user);
            List<Payslip> checked = new ArrayList<>(payslips.findApprovedForUpdate(user));
            BigDecimal requestedMinutes = BigDecimal.ZERO;
            Map<Long, Payslip> provisional = new LinkedHashMap<>();
            for (Payslip slip : batch.stream().sorted(Comparator.comparing(Payslip::getPeriodEnd, Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
                if (slip.isApproved() || !Objects.equals(slip.getUser().getId(), user.getId())) continue;
                validateAmounts(slip);
                if (slip.isCorrection()) calculations.validateDraft(slip, provisional); else calculations.validateDraft(slip);
                PayrollReviewService.validatePeriod(slip.getPeriodStart(), slip.getPeriodEnd());
                for (Payslip other : checked) {
                    if (!Objects.equals(other.getId(), slip.getId()) && PayrollReviewService.overlaps(other, slip.getPeriodStart(), slip.getPeriodEnd())
                            && !(slip.isCorrection() && (Objects.equals(other.getId(), slip.getCorrectionOfId()) || Objects.equals(other.getCorrectionOfId(), slip.getCorrectionOfId())))) {
                        throw new IllegalStateException("Abrechnung #" + slip.getId() + " überschneidet sich mit Abrechnung #" + other.getId() + ". Bitte zuerst den Korrekturfall klären.");
                    }
                }
                checked.add(slip);
                if (slip.isCorrection()) {
                    if (provisional.put(slip.getCorrectionOfId(), slip) != null) throw new IllegalStateException("Nur ein Korrekturlauf je Original kann gemeinsam freigegeben werden.");
                }
                if (slip.isPayoutOvertime()) {
                    if (slip.getOvertimeHours() == null || !Double.isFinite(slip.getOvertimeHours()) || slip.getOvertimeHours() == 0 || !slip.isCorrection() && slip.getOvertimeHours() < 0) {
                        throw new IllegalStateException("Die Überstundenauszahlung benötigt eine positive, endliche Stundenanzahl.");
                    }
                    int minutes = com.chrono.chrono.services.payroll.PayrollGrossCalculator.payoutMinutes(Math.abs(slip.getOvertimeHours())) * (slip.getOvertimeHours() < 0 ? -1 : 1);
                    requestedMinutes = requestedMinutes.add(BigDecimal.valueOf(minutes));
                    if (requestedMinutes.compareTo(BigDecimal.valueOf(Math.max(0, tracking.getPayrollOvertimeBalance(user, slip.getPeriodEnd())))) > 0) throw new IllegalStateException("Die gesammelten Auszahlungen übersteigen den bis zum jeweiligen Periodenende erarbeiteten Saldo.");
                }
            }
            if (requestedMinutes.signum() > 0 && (user.getTrackingBalanceInMinutes() == null
                    || requestedMinutes.compareTo(BigDecimal.valueOf(Math.max(0, user.getTrackingBalanceInMinutes()))) > 0)) {
                throw new IllegalStateException("Die Überstundenauszahlung übersteigt den aktuell verfügbaren Saldo.");
            }
        }
    }

    public static void validateAmounts(Payslip slip) {
        BigDecimal gross = money(slip.getGrossSalary(), "Bruttolohn");
        BigDecimal deductions = money(slip.getDeductions(), "Abzüge");
        BigDecimal net = money(slip.getNetSalary(), "Nettolohn");
        BigDecimal employer = money(slip.getEmployerContributions(), "Arbeitgeberbeiträge");
        if (!slip.isCorrection() && (gross.signum() < 0 || net.signum() < 0)) throw new IllegalStateException("Negativer Brutto-/Nettolohn benötigt einen ausdrücklich zugeordneten Korrekturbeleg.");
        if (gross.subtract(deductions).compareTo(net) != 0) throw new IllegalStateException("Brutto minus Abzüge stimmt nicht mit dem Nettolohn überein.");
        checkComponents(slip.getEarnings(), gross, "Lohnbestandteile");
        checkComponents(slip.getDeductionsList(), deductions, "Abzugspositionen");
        checkComponents(slip.getEmployerContribList(), employer, "Arbeitgeberpositionen");
    }

    private static BigDecimal money(Double value, String name) {
        if (value == null || !Double.isFinite(value)) throw new IllegalStateException(name + " fehlt oder ist kein gültiger Centbetrag.");
        BigDecimal amount = BigDecimal.valueOf(value), rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.subtract(rounded).abs().compareTo(new BigDecimal("0.000000001")) > 0) throw new IllegalStateException(name + " ist kein gültiger Centbetrag.");
        return rounded;
    }

    private static void checkComponents(List<PayComponent> components, BigDecimal expected, String name) {
        if (components == null || components.isEmpty()) {
            if (expected.signum() != 0) throw new IllegalStateException(name + " fehlen für den ausgewiesenen Gesamtbetrag.");
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PayComponent component : components) sum = sum.add(money(component.getAmount(), name));
        if (sum.compareTo(expected) != 0) throw new IllegalStateException(name + " stimmen nicht mit dem Gesamtbetrag überein.");
    }
}
