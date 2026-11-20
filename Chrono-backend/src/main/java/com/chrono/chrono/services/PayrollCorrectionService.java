package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.PayrollGrossCalculator;
import com.chrono.chrono.services.payroll.PayrollYearAssignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Atomic, chronological correction batches. Original documents and previous revisions stay immutable. */
@Service
public class PayrollCorrectionService {
    private final PayslipRepository slips;
    private final UserRepository users;
    private final PayslipAuditRepository audit;
    private final PayrollLedgerService ledger;
    private final PayrollCalculationService calculations;
    private final EntityManager entities;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PayrollOpeningService openings;
    public PayrollCorrectionService(PayslipRepository slips, UserRepository users, PayslipAuditRepository audit,
            PayrollLedgerService ledger, PayrollCalculationService calculations, EntityManager entities) {
        this.slips = slips; this.users = users; this.audit = audit; this.ledger = ledger; this.calculations = calculations; this.entities = entities;
    }
    public record Request(String reason, LocalDate payoutDate, Double replacementOvertimeHours,PayrollYearAssignment yearAssignment,Long openingVersionId) {
        public Request(String reason,LocalDate date,Double hours){this(reason,date,hours,null,null);}
        public Request(String reason,LocalDate date,Double hours,PayrollYearAssignment assignment){this(reason,date,hours,assignment,null);}
    }
    @Transactional
    public void discard(Long memberId) {
        var member = slips.findById(memberId).orElseThrow();
        var user = users.findByIdForUpdate(member.getUser().getId()).orElseThrow(); entities.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        entities.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        if (!member.isCorrection() || member.getCorrectionBatch() == null) throw new IllegalStateException("Kein Korrekturlauf.");
        var group = slips.findByCorrectionBatch(member.getCorrectionBatch());
        group.forEach(slip -> entities.refresh(slip, LockModeType.PESSIMISTIC_WRITE));
        if (group.stream().anyMatch(Payslip::isApproved)) throw new IllegalStateException("Freigegebene Korrekturbelege bleiben erhalten.");
        if(openings!=null)openings.discardBatch(member.getCorrectionBatch());
        for (var slip : group) audit.deleteByPayslip(slip);
        slips.deleteAll(group);
    }
    @Transactional
    public List<Payslip> generate(Long originalId, Request input, String actor) {
        if (input == null || input.reason() == null || input.reason().isBlank() || input.reason().length() > 500 || input.payoutDate() == null)
            throw new IllegalArgumentException("Korrekturgrund (maximal 500 Zeichen) und Auszahlungs-/Verrechnungsdatum sind erforderlich.");
        if (input.replacementOvertimeHours() != null && (!Double.isFinite(input.replacementOvertimeHours()) || input.replacementOvertimeHours() < 0)) throw new IllegalArgumentException("Ungültige Ersatz-Überstunden.");
        Payslip selected = slips.findById(originalId).orElseThrow();
        User user = users.findByIdForUpdate(selected.getUser().getId()).orElseThrow();
        entities.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        entities.refresh(selected, LockModeType.PESSIMISTIC_WRITE);
        if (!selected.isApproved() || selected.isCorrection()) throw new IllegalStateException("Den freigegebenen Originalbeleg zur Rückrechnung auswählen.");
        validateCorrectionDate(selected.getPeriodStart(), input.payoutDate(),input.yearAssignment());
        boolean financialOnly=ledger.latest(selected).getCalculationSnapshot()!=null||input.openingVersionId()!=null;
        var originals = ledger.originals(user).stream().filter(p -> p.getPeriodStart().getYear() == selected.getPeriodStart().getYear()
                && !p.getPeriodStart().isBefore(selected.getPeriodStart()))
                // An opening revision repairs new financial payroll, never adopts old hours-only slips.
                .filter(p -> !financialOnly || ledger.latest(p).getCalculationSnapshot()!=null).toList();
        for (int i = 1; i < originals.size(); i++) if (!originals.get(i).getPeriodStart().isAfter(originals.get(i - 1).getPeriodEnd())) throw new IllegalStateException("Überlappende Altbelege müssen vor einer automatischen Rückrechnung fachlich zugeordnet werden.");
        String batch = UUID.randomUUID().toString();
        List<Payslip> result = new ArrayList<>(); Map<Long, Payslip> provisional = new LinkedHashMap<>();
        for (Payslip original : originals) {
            Payslip previous = ledger.latest(original);
            int oldMinutes = calculations.effectiveOvertimeMinutes(original, previous);
            double hours = original.getId().equals(originalId) && input.replacementOvertimeHours() != null ? input.replacementOvertimeHours() : oldMinutes / 60.0;
            var calculation = calculations.calculate(user, original.getPeriodStart(), original.getPeriodEnd(), hours, hours > 0, provisional, oldMinutes,input.openingVersionId())
                    .withCorrection(new PayrollCalculationService.Correction(ledger.reference(original, previous), input.reason().strip(), hours,input.yearAssignment(),input.openingVersionId(),financialOnly));
            if(input.payoutDate().getYear()!=selected.getPeriodStart().getYear()) {
                LocalDate closing=calculation.terms().exitDate()!=null&&calculation.terms().exitDate().getYear()==selected.getPeriodStart().getYear()
                        ?calculation.terms().exitDate():LocalDate.of(selected.getPeriodStart().getYear(),12,31);
                if(originals.isEmpty() || !originals.get(originals.size()-1).getPeriodEnd().equals(closing)) throw new IllegalStateException("Für die Vorjahresberichtigung fehlt der vollständige Jahres-/Austrittsabschluss. Spätere tatsächliche Löhne dürfen nicht prognostiziert werden.");
            }
            if(input.payoutDate().getYear()!=selected.getPeriodStart().getYear() && "DE".equals(calculation.terms().country())
                    && originals.stream().noneMatch(p -> p.getPeriodEnd().equals(p.getPeriodEnd().withMonth(12).withDayOfMonth(31))))
                throw new IllegalStateException("Die deutsche Vorjahresberichtigung benötigt die vollständige Dezember-Abrechnung für den Jahresausgleich.");
            calculation=calculations.applyYearAssignment(user,calculation,input.payoutDate(),input.yearAssignment(),provisional);
            validateCorrectionPayment(calculation, original, previous, input.payoutDate(), ledger);
            String currency = original.getCurrency() != null ? original.getCurrency() : ("DE".equalsIgnoreCase(original.getUser().getCountry()) ? "EUR" : "CHF");
            if (!currency.equals(calculation.terms().currency())) throw new IllegalStateException("Eine Rückrechnung darf die Währung des Originalbelegs nicht ändern.");
            Payslip delta = new Payslip(); delta.setUser(user); delta.setPeriodStart(original.getPeriodStart()); delta.setPeriodEnd(original.getPeriodEnd());
            delta.setCorrectionOfId(original.getId()); delta.setSupersedesCorrectionId(previous.isCorrection() ? previous.getId() : null); delta.setCorrectionBatch(batch);
            delta.setCurrency(currency); delta.setCalculationVersion(PayrollCalculationService.VERSION); delta.setCalculationSnapshot(calculations.snapshot(calculation));
            delta.setPayoutDate(input.payoutDate()); delta.setBankAccount(user.getBankAccount()); delta.setSocialSecurityNumber(user.getSocialSecurityNumber());
            delta.setPayType(calculation.terms().hourly() ? "hourly" : "salary");
            fillDelta(delta, calculation, original, previous, ledger);
            int minutes = calculation.gross().paidOvertimeMinutes() - oldMinutes;
            delta.setOvertimeHours(minutes / 60.0); delta.setPayoutOvertime(minutes != 0);
            PayrollApprovalGuard.validateAmounts(delta);
            slips.saveAndFlush(delta); // IDs are part of the immutable references for subsequent months.
            audit.save(new PayslipAudit(delta, "CORRECTION_GENERATED", actor, "Original #" + original.getId() + "; " + input.reason().strip()));
            provisional.put(original.getId(), delta); result.add(delta);
        }
        return List.copyOf(result);
    }
    public static void validateCorrectionDate(LocalDate periodStart, LocalDate payoutDate) {
        validateCorrectionDate(periodStart,payoutDate,null);
    }
    public static void validateCorrectionDate(LocalDate periodStart, LocalDate payoutDate,PayrollYearAssignment assignment) {
        if(periodStart==null || payoutDate==null || payoutDate.isBefore(periodStart)) throw new IllegalStateException("Gültiges Verrechnungsdatum ab der Originalperiode erforderlich.");
        if(payoutDate.getYear()==periodStart.getYear()) return;
        LocalDate deadline=LocalDate.of(periodStart.getYear()+1,3,31);
        if(assignment==null || !Boolean.TRUE.equals(assignment.originalGrossAlreadyPaid()) || payoutDate.getYear()!=periodStart.getYear()+1
                || payoutDate.isAfter(deadline) || LocalDate.now(ZoneId.of("Europe/Zurich")).isAfter(deadline))
            throw new IllegalStateException("Vorjahresberichtigungen benötigen die ausdrückliche Zuordnung bereits bezahlter Originaljahreslöhne und müssen innerhalb der gesetzlichen Folgejahresfrist erfolgen. Neue Nachzahlungen benötigen Regeln des Zuflussjahres.");
    }
    public static Map<String, Double> earnings(PayrollCalculationService.Calculation c) {
        return PayrollGrossCalculator.earnings(c.gross());
    }
    private static Map<String, Double> components(List<PayComponent> list, Double total, String missingLabel) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (PayComponent p : list) result.merge(p.getType(), p.getAmount(), (a,b) -> BigDecimal.valueOf(a).add(BigDecimal.valueOf(b)).doubleValue());
        // Preserve and visibly reconcile legacy total/component discrepancies, never silently discard them.
        BigDecimal sum = result.values().stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = BigDecimal.valueOf(total).subtract(sum);
        if (difference.signum() != 0) result.put(missingLabel, difference.doubleValue());
        return result;
    }
    private static void fillDelta(Payslip delta, PayrollCalculationService.Calculation c, Payslip original, Payslip previous, PayrollLedgerService ledger) {
        var baseline = previous.isCorrection() ? ledger.read(previous) : null;
        BigDecimal oldGross = baseline != null ? baseline.gross().total() : money(original.getGrossSalary());
        Map<String, Double> oldEmployee = baseline != null ? baseline.employee() : components(original.getDeductionsList(), original.getDeductions(), "Legacy deduction difference");
        Map<String, Double> oldEmployer = baseline != null ? baseline.employer() : components(original.getEmployerContribList(), original.getEmployerContributions(), "Legacy employer difference");
        Map<String, Double> oldEarnings = baseline != null ? earnings(baseline) : components(original.getEarnings(), original.getGrossSalary(), "Legacy earnings difference");
        delta.setEarnings(difference(earnings(c), oldEarnings)); delta.setDeductionsList(difference(c.employee(), oldEmployee)); delta.setEmployerContribList(difference(c.employer(), oldEmployer));
        delta.setGrossSalary(c.gross().total().subtract(oldGross).doubleValue());
        delta.setDeductions(sum(delta.getDeductionsList())); delta.setEmployerContributions(sum(delta.getEmployerContribList()));
        BigDecimal effectiveNet = c.gross().total().subtract(total(c.employee()));
        BigDecimal priorNet = baseline != null ? baseline.gross().total().subtract(total(baseline.employee())) : money(original.getNetSalary());
        delta.setNetSalary(effectiveNet.subtract(priorNet).doubleValue());
        // An inconsistent legacy net needs an explicit reconciliation line so original + delta equals the corrected net.
        BigDecimal balance = money(delta.getGrossSalary()).subtract(money(delta.getDeductions())).subtract(money(delta.getNetSalary()));
        if (balance.signum() != 0) {
            delta.getDeductionsList().add(new PayComponent("Legacy net reconciliation", balance.doubleValue()));
            delta.setDeductions(sum(delta.getDeductionsList()));
        }
    }
    public static void validateDelta(Payslip slip, PayrollCalculationService.Calculation c, Payslip original, Payslip previous, PayrollLedgerService ledger) {
        validateCorrectionPayment(c, original, previous, slip.getPayoutDate(), ledger);
        Payslip expected = new Payslip(); fillDelta(expected, c, original, previous, ledger);
        if (!Objects.equals(slip.getGrossSalary(), expected.getGrossSalary()) || !Objects.equals(slip.getDeductions(), expected.getDeductions())
                || !Objects.equals(slip.getNetSalary(), expected.getNetSalary()) || !Objects.equals(slip.getEmployerContributions(), expected.getEmployerContributions())
                || !asMap(slip.getEarnings()).equals(asMap(expected.getEarnings())) || !asMap(slip.getDeductionsList()).equals(asMap(expected.getDeductionsList()))
                || !asMap(slip.getEmployerContribList()).equals(asMap(expected.getEmployerContribList())) || !c.terms().currency().equals(slip.getCurrency()))
            throw new IllegalStateException("Korrekturdifferenzen stimmen nicht mit Original und neuer Berechnung überein.");
        int oldMinutes = previous.getCalculationSnapshot() != null ? ledger.read(previous).gross().paidOvertimeMinutes()
                : original.isPayoutOvertime() ? PayrollGrossCalculator.payoutMinutes(original.getOvertimeHours()) : 0;
        int deltaMinutes = c.gross().paidOvertimeMinutes() - oldMinutes;
        if (!Objects.equals(slip.getOvertimeHours(), deltaMinutes / 60.0) || slip.isPayoutOvertime() != (deltaMinutes != 0)) throw new IllegalStateException("Überstunden-Korrekturdifferenz stimmt nicht.");
    }
    private static void validateCorrectionPayment(PayrollCalculationService.Calculation current, Payslip original,
            Payslip previous, LocalDate payoutDate, PayrollLedgerService ledger) {
        var baseline = previous.getCalculationSnapshot() != null ? ledger.read(previous) : null;
        if(payoutDate.getYear()!=original.getPeriodStart().getYear()) {
            BigDecimal previousGross=baseline==null?money(original.getGrossSalary()):baseline.gross().total();
            if(current.gross().total().compareTo(previousGross)!=0) throw new IllegalStateException("Ein geänderter Vorjahresbruttolohn benötigt die getrennte Zuordnung tatsächlicher Zahlungen und des Zuflussjahrs. Die Vorjahresfunktion berichtigt Steuer-/SV-Abzüge auf unverändertem, bereits bezahltem Brutto.");
        }
        if (!"DE".equals(current.terms().country()) || YearMonth.from(original.getPeriodEnd()).equals(YearMonth.from(payoutDate))) return;
        boolean oneOff = current.terms().thirteenthMonths() > 1 || baseline != null && baseline.terms().thirteenthMonths() > 1;
        BigDecimal oldThirteenth = baseline != null ? baseline.gross().thirteenthAmount()
                : original.getEarnings().stream().filter(p -> "Thirteenth salary".equals(p.getType()))
                    .map(p -> money(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (oneOff && current.gross().thirteenthAmount().compareTo(oldThirteenth) != 0) {
            BigDecimal oldGross=baseline==null?money(original.getGrossSalary()):baseline.gross().total();
            if(current.correction()==null || current.correction().yearAssignment()==null || current.gross().total().compareTo(oldGross)!=0)
                throw new IllegalStateException("Eine deutsche Rückrechnung mit geändertem einmaligem 13. Monatslohn und anderem Auszahlungsmonat benötigt die Steuer- und SV-Grundlagen des tatsächlichen Zuflussmonats. Als Originalperioden-Berichtigung ist nur eine belegte Umgliederung auf unverändertem bereits bezahltem Brutto zulässig.");
        }
    }
    private static Map<String, Double> asMap(List<PayComponent> values) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (var value : values) if (result.put(value.getType(), value.getAmount()) != null) throw new IllegalStateException("Doppelte Korrekturposition.");
        return result;
    }
    private static List<PayComponent> difference(Map<String, Double> next, Map<String, Double> previous) {
        Set<String> keys = new LinkedHashSet<>(next.keySet()); keys.addAll(previous.keySet()); List<PayComponent> result = new ArrayList<>();
        for (String key : keys) { BigDecimal value = money(next.getOrDefault(key, 0.0)).subtract(money(previous.getOrDefault(key, 0.0))); if (value.signum() != 0) result.add(new PayComponent(key, value.doubleValue())); }
        return result;
    }
    private static BigDecimal money(Double value) { if (value == null || !Double.isFinite(value)) throw new IllegalStateException("Unvollständiger Altbeleg: Betrag fehlt."); return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal total(Map<String, Double> values) { return values.values().stream().map(PayrollCorrectionService::money).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static double sum(List<PayComponent> values) { return values.stream().map(p -> money(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue(); }
}
