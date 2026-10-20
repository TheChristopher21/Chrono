package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class PayrollCalculationService {
    public static final String VERSION = "chrono-payroll-2026-v4";
    private final PayrollTermsService terms;
    private final TimeTrackingService tracking;
    private final TaxCalculationService tax;
    private final ObjectMapper json;
    private final EmploymentModelHistoryService employment;
    private final PayrollLedgerService ledger;
    public PayrollCalculationService(PayrollTermsService terms, TimeTrackingService tracking, TaxCalculationService tax, ObjectMapper json, EmploymentModelHistoryService employment, PayrollLedgerService ledger) {
        this.terms = terms; this.tracking = tracking; this.tax = tax; this.json = json; this.employment = employment; this.ledger = ledger;
    }
    public record Correction(PayrollLedgerService.Reference baseline, String reason, Double replacementOvertimeHours) {}
    public record Calculation(Long rulesVersion, PayrollTerms terms, LocalDate periodStart, LocalDate periodEnd, List<DailyTimeSummaryDTO> days,
            PayrollGrossCalculator.Result gross, Map<String, Double> employee, Map<String, Double> employer, String taxReference,
            SwissPayrollSettlement.Basis swissBasis, GermanPayrollTax.Basis germanBasis, List<PayrollLedgerService.Reference> history, Correction correction) {
        public Calculation(Long rulesVersion, PayrollTerms terms, LocalDate start, LocalDate end, List<DailyTimeSummaryDTO> days, PayrollGrossCalculator.Result gross, Map<String, Double> employee, Map<String, Double> employer, String reference) {
            this(rulesVersion, terms, start, end, days, gross, employee, employer, reference, null, null, List.of(), null);
        }
        public Calculation withCorrection(Correction value) { return new Calculation(rulesVersion, terms, periodStart, periodEnd, days, gross, employee, employer, taxReference, swissBasis, germanBasis, history, value); }
    }
    public Calculation calculate(User user, LocalDate start, LocalDate end, Double hours, boolean payout) {
        return calculate(user, start, end, hours, payout, Map.of(), 0);
    }
    public Calculation calculate(User user, LocalDate start, LocalDate end, Double hours, boolean payout, Map<Long, Payslip> provisional, int restoredMinutes) {
        PayrollReviewService.validatePeriod(start, end);
        if (!end.isBefore(LocalDate.now(ZoneId.of("Europe/Zurich")))) throw new IllegalStateException("Nur vollständig abgeschlossene Tage können abgerechnet werden.");
        var resolved = terms.resolve(user, start, end);
        TaxCalculationService.validateSupportedPeriod(resolved.terms(), start, end);
        LocalDate timeStart = resolved.terms().hourly() ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : start;
        if (timeStart.isBefore(resolved.terms().entryDate())) timeStart = resolved.terms().entryDate();
        if (timeStart.isBefore(start) && !terms.resolve(user, timeStart, end).versionId().equals(resolved.versionId())) throw new IllegalStateException("Stundenlohnregeln ändern innerhalb einer Abrechnungswoche. Die Woche muss gesondert aufgeteilt werden.");
        for (var dated : employment.resolveUserSnapshotsForRange(user, timeStart, end).entrySet()) {
            var historical = dated.getValue();
            if (Boolean.TRUE.equals(historical.getIsHourly()) != resolved.terms().hourly()) throw new IllegalStateException("Lohnart und historisches Arbeitszeitmodell stimmen nicht überein.");
            if (payout && !resolved.terms().hourly()) validateOvertimeContract(resolved.terms(), historical, dated.getKey());
        }
        var days = tracking.getUserPeriodSummary(user, timeStart, end).getDailySummaries();
        int earlierDeltaMinutes = 0;
        LocalDate accountStart = payout ? tracking.getPayrollOvertimeAccountStart(user, end) : null;
        for (Payslip delta : provisional.values()) if (delta.isPayoutOvertime() && delta.getOvertimeHours() != null
                && Objects.equals(accountStart, tracking.getPayrollOvertimeAccountStart(user, delta.getPeriodEnd())))
            earlierDeltaMinutes = Math.addExact(earlierDeltaMinutes, (int) Math.round(delta.getOvertimeHours() * 60));
        // Already paid minutes may be retained even if later absences made the current balance negative.
        // Only an additional payout consumes available credit; earlier reversals first offset any debt.
        Integer balance = null;
        if (payout) {
            int available = tracking.getPayrollOvertimeBalance(user, end);
            if (Objects.equals(accountStart, tracking.getPayrollOvertimeAccountStart(user, LocalDate.now(ZoneId.of("Europe/Zurich")))))
                available = Math.min(available, Objects.requireNonNullElse(user.getTrackingBalanceInMinutes(), 0));
            balance = Math.addExact(Math.max(0, Math.subtractExact(available, earlierDeltaMinutes)), restoredMinutes);
        }
        var gross = PayrollGrossCalculator.calculate(resolved.terms(), start, end, days, hours, payout, balance);
        var history = ledger.before(user, resolved.terms(), start, provisional);
        gross = gross.withThirteenth(ledger.thirteenth(resolved.terms(), start, end, gross, history));
        var deductions = tax.calculateRun(user, gross, start, end, resolved.terms(), history);
        return new Calculation(resolved.versionId(), resolved.terms(), start, end, days, gross, deductions.getEmployee(), deductions.getEmployer(), deductions.getConfigurationReference(), deductions.getSwissBasis(), deductions.getGermanBasis(), history.references(), null);
    }
    public String snapshot(Calculation calculation) {
        try { return json.writeValueAsString(calculation); }
        catch (Exception e) { throw new IllegalStateException("Berechnungsgrundlagen konnten nicht gesichert werden.", e); }
    }
    private static void validateOvertimeContract(PayrollTerms rules, User historical, LocalDate date) {
        int agreed = rules.weeklyHours().multiply(java.math.BigDecimal.valueOf(60)).intValueExact();
        if (Boolean.TRUE.equals(historical.getIsPercentage())) {
            // The percentage time model uses a 40-hour full-time week (WorkScheduleService).
            if (agreed == Math.round(2400 * historical.getWorkPercentage() / 100.0)) return;
        } else if (!historical.getWeeklySchedule().isEmpty() && historical.getScheduleCycle() != null
                && historical.getScheduleCycle() > 0 && (historical.getScheduleEffectiveDate() == null || !date.isBefore(historical.getScheduleEffectiveDate()))) {
            boolean matches = historical.getWeeklySchedule().stream().allMatch(week -> {
                int minutes = 0;
                for (DayOfWeek day : DayOfWeek.values()) minutes += (int) Math.round(week.getOrDefault(day.name().toLowerCase(java.util.Locale.ROOT), 0.0) * 60);
                return minutes == agreed;
            });
            if (matches) return;
        } else if (historical.getDailyWorkHours() != null && historical.getExpectedWorkDays() != null
                && Math.round(historical.getDailyWorkHours() * 60) * historical.getExpectedWorkDays() == agreed) return;
        throw new IllegalStateException("Vertragliche Wochenstunden und das historische Arbeitszeitmodell stimmen nicht überein. Vor einer Überstundenauszahlung die beiden Grundlagen abstimmen.");
    }
    public void validateDraft(Payslip slip) {
        validateDraft(slip, Map.of());
    }
    public void validateDraft(Payslip slip, Map<Long, Payslip> provisional) {
        if (!VERSION.equals(slip.getCalculationVersion()) || slip.getCalculationSnapshot() == null) throw new IllegalStateException("Dieser Entwurf stammt aus der alten Berechnung. Bitte einen neuen Entwurf mit den datierten Lohnregeln erstellen; der alte Entwurf wird nicht überschrieben.");
        var saved = ledger.read(slip);
        Calculation current;
        Payslip original = null, previous = null;
        if (slip.isCorrection()) {
            PayrollCorrectionService.validateCorrectionDate(slip.getPeriodStart(), slip.getPayoutDate());
            original = ledger.originals(slip.getUser()).stream().filter(p -> p.getId().equals(slip.getCorrectionOfId())).findFirst().orElseThrow();
            previous = ledger.latest(original);
            if (saved.correction() == null || !ledger.reference(original, previous).equals(saved.correction().baseline())) throw new IllegalStateException("Die Korrekturbasis wurde inzwischen geändert. Korrekturlauf neu erstellen.");
            if (!Objects.equals(slip.getSupersedesCorrectionId(), previous.isCorrection() ? previous.getId() : null)) throw new IllegalStateException("Korrekturverkettung stimmt nicht mit dem letzten Beleg überein.");
            Double hours = saved.correction().replacementOvertimeHours();
            int restored = effectiveOvertimeMinutes(original, previous);
            current = calculate(slip.getUser(), slip.getPeriodStart(), slip.getPeriodEnd(), hours, hours != null && hours > 0, provisional, restored).withCorrection(saved.correction());
        } else current = calculate(slip.getUser(), slip.getPeriodStart(), slip.getPeriodEnd(), slip.getOvertimeHours(), slip.isPayoutOvertime());
        try {
            if (!json.readTree(slip.getCalculationSnapshot()).equals(json.readTree(snapshot(current)))) throw new IllegalStateException("Zeitdaten oder Lohngrundlagen haben sich seit der Erstellung geändert. Bitte den Entwurf neu erstellen.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Berechnungsnachweis ist nicht lesbar.", e); }
        if (slip.isCorrection()) {
            PayrollCorrectionService.validateDelta(slip, current, original, previous, ledger);
            return;
        }
        var amounts = new TaxCalculationService.Result(current.employee(), current.employer());
        if (current.gross().total().compareTo(java.math.BigDecimal.valueOf(slip.getGrossSalary())) != 0
                || Double.compare(amounts.getEmployeeTotal(), slip.getDeductions()) != 0
                || Double.compare(amounts.getEmployerTotal(), slip.getEmployerContributions()) != 0
                || !current.terms().currency().equals(slip.getCurrency())) throw new IllegalStateException("Abrechnungsbeträge stimmen nicht mit dem gesicherten Berechnungsnachweis überein.");
        compareComponents(slip.getDeductionsList(), current.employee()); compareComponents(slip.getEmployerContribList(), current.employer());
        Map<String, Double> earnings = new LinkedHashMap<>(); earnings.put("Base salary", current.gross().base().doubleValue());
        if (current.gross().premium().signum() > 0) earnings.put("Overtime premium", current.gross().premium().doubleValue());
        if (current.gross().paidOvertimeMinutes() > 0) earnings.put("Overtime payout", current.gross().payout().doubleValue());
        if (current.gross().thirteenthAmount().signum() > 0) earnings.put("Thirteenth salary", current.gross().thirteenthAmount().doubleValue());
        compareComponents(slip.getEarnings(), earnings);
    }
    public int effectiveOvertimeMinutes(Payslip original, Payslip revision) {
        if (revision.getCalculationSnapshot() != null) return ledger.read(revision).gross().paidOvertimeMinutes();
        return original.isPayoutOvertime() && original.getOvertimeHours() != null ? PayrollGrossCalculator.payoutMinutes(original.getOvertimeHours()) : 0;
    }
    private static void compareComponents(List<PayComponent> actual, Map<String, Double> expected) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (var c : actual) if (values.put(c.getType(), c.getAmount()) != null) throw new IllegalStateException("Doppelte Abrechnungsposition.");
        if (!values.equals(expected)) throw new IllegalStateException("Positionen stimmen nicht mit dem Berechnungsnachweis überein.");
    }
}
