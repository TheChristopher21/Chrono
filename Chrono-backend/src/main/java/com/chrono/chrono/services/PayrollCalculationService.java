package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.*;
import java.math.BigDecimal;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class PayrollCalculationService {
    public static final String VERSION = "chrono-payroll-2026-v8";
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PayrollHistoricalSettlementService historical;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PayrollPaymentLedgerService payments;
    private final PayrollTermsService terms;
    private final TimeTrackingService tracking;
    private final TaxCalculationService tax;
    private final ObjectMapper json;
    private final EmploymentModelHistoryService employment;
    private final PayrollLedgerService ledger;
    private final Optional<PayrollPeriodInputService> inputs;
    public PayrollCalculationService(PayrollTermsService terms, TimeTrackingService tracking, TaxCalculationService tax, ObjectMapper json, EmploymentModelHistoryService employment, PayrollLedgerService ledger) {
        this(terms,tracking,tax,json,employment,ledger,Optional.empty());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PayrollCalculationService(PayrollTermsService terms, TimeTrackingService tracking, TaxCalculationService tax, ObjectMapper json, EmploymentModelHistoryService employment, PayrollLedgerService ledger,Optional<PayrollPeriodInputService> inputs) {
        this.terms = terms; this.tracking = tracking; this.tax = tax; this.json = json; this.employment = employment; this.ledger = ledger; this.inputs=inputs;
    }
    public record Correction(PayrollLedgerService.Reference baseline, String reason, Double replacementOvertimeHours,PayrollYearAssignment yearAssignment,Long openingVersionId,Boolean financialOnly) {
        public Correction(PayrollLedgerService.Reference baseline,String reason,Double hours,PayrollYearAssignment assignment,Long opening){this(baseline,reason,hours,assignment,opening,null);}
        public Correction(PayrollLedgerService.Reference baseline,String reason,Double hours){this(baseline,reason,hours,null,null);}
        public Correction(PayrollLedgerService.Reference baseline,String reason,Double hours,PayrollYearAssignment assignment){this(baseline,reason,hours,assignment,null);}
    }
    public record Calculation(Long rulesVersion, PayrollTerms terms, LocalDate periodStart, LocalDate periodEnd, List<DailyTimeSummaryDTO> days,
            PayrollGrossCalculator.Result gross, Map<String, Double> employee, Map<String, Double> employer, String taxReference,
            SwissPayrollSettlement.Basis swissBasis, GermanPayrollTax.Basis germanBasis, List<PayrollLedgerService.Reference> history, Correction correction,
            PayrollPeriodInputService.Reference periodInputs,PayrollRemuneration remuneration) {
        public Calculation(Long rulesVersion,PayrollTerms terms,LocalDate start,LocalDate end,List<DailyTimeSummaryDTO> days,
                PayrollGrossCalculator.Result gross,Map<String,Double> employee,Map<String,Double> employer,String reference,
                SwissPayrollSettlement.Basis swissBasis,GermanPayrollTax.Basis germanBasis,List<PayrollLedgerService.Reference> history,Correction correction,PayrollPeriodInputService.Reference inputs) {
            this(rulesVersion,terms,start,end,days,gross,employee,employer,reference,swissBasis,germanBasis,history,correction,inputs,null);
        }
        public Calculation(Long rulesVersion,PayrollTerms terms,LocalDate start,LocalDate end,List<DailyTimeSummaryDTO> days,
                PayrollGrossCalculator.Result gross,Map<String,Double> employee,Map<String,Double> employer,String reference,
                SwissPayrollSettlement.Basis swissBasis,GermanPayrollTax.Basis germanBasis,List<PayrollLedgerService.Reference> history,Correction correction) {
            this(rulesVersion,terms,start,end,days,gross,employee,employer,reference,swissBasis,germanBasis,history,correction,null);
        }
        public Calculation(Long rulesVersion, PayrollTerms terms, LocalDate start, LocalDate end, List<DailyTimeSummaryDTO> days, PayrollGrossCalculator.Result gross, Map<String, Double> employee, Map<String, Double> employer, String reference) {
            this(rulesVersion, terms, start, end, days, gross, employee, employer, reference, null, null, List.of(), null);
        }
        public Calculation withCorrection(Correction value) { return new Calculation(rulesVersion, terms, periodStart, periodEnd, days, gross, employee, employer, taxReference, swissBasis, germanBasis, history, value,periodInputs,remuneration); }
        public Calculation withGross(PayrollGrossCalculator.Result value) { return new Calculation(rulesVersion,terms,periodStart,periodEnd,days,value,employee,employer,taxReference,swissBasis,germanBasis,history,correction,periodInputs,remuneration); }
    }
    public Calculation calculate(User user, LocalDate start, LocalDate end, Double hours, boolean payout) {
        return calculate(user, start, end, hours, payout, Map.of(), 0);
    }
    public Calculation calculate(User user, LocalDate start, LocalDate end, Double hours, boolean payout, Map<Long, Payslip> provisional, int restoredMinutes) {
        return calculate(user,start,end,hours,payout,provisional,restoredMinutes,null);
    }
    public Calculation calculate(User user,LocalDate start,LocalDate end,Double hours,boolean payout,Map<Long,Payslip> provisional,int restoredMinutes,Long openingVersionId) {
        PayrollReviewService.validatePeriod(start, end);
        if (!end.isBefore(payrollToday())) throw new IllegalStateException("Nur vollständig abgeschlossene Tage können abgerechnet werden.");
        var resolved = terms.resolve(user, start, end);
        var t=resolved.terms();boolean cashOnly=t.exitDate()!=null&&start.isAfter(t.exitDate());
        if(cashOnly)TaxCalculationService.validatePostEmploymentPeriod(t,start,end);else TaxCalculationService.validateSupportedPeriod(t, start, end);
        LocalDate timeStart = resolved.terms().hourly() ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : start;
        if (timeStart.isBefore(resolved.terms().entryDate())) timeStart = resolved.terms().entryDate();
        if (!cashOnly&&timeStart.isBefore(start) && !terms.resolve(user, timeStart, end).versionId().equals(resolved.versionId())) throw new IllegalStateException("Stundenlohnregeln ändern innerhalb einer Abrechnungswoche. Die Woche muss gesondert aufgeteilt werden.");
        for (var dated : (cashOnly?Map.<LocalDate,User>of():employment.resolveUserSnapshotsForRange(user, timeStart, end)).entrySet()) {
            var historical = dated.getValue();
            if (Boolean.TRUE.equals(historical.getIsHourly()) != resolved.terms().hourly()) throw new IllegalStateException("Lohnart und historisches Arbeitszeitmodell stimmen nicht überein.");
            if (payout && !resolved.terms().hourly()) validateOvertimeContract(resolved.terms(), historical, dated.getKey());
        }
        if(cashOnly&&payout)throw new IllegalStateException("Nach Austritt keine erneute Zeitkontoauszahlung über eine zusätzliche Nachzahlungsperiode.");
        var days = cashOnly?List.<DailyTimeSummaryDTO>of():tracking.getUserPeriodSummary(user, timeStart, end).getDailySummaries();
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
            if (Objects.equals(accountStart, tracking.getPayrollOvertimeAccountStart(user, payrollToday())))
                available = Math.min(available, Objects.requireNonNullElse(user.getTrackingBalanceInMinutes(), 0));
            balance = Math.addExact(Math.max(0, Math.subtractExact(available, earlierDeltaMinutes)), restoredMinutes);
        }
        var periodInput=inputs.flatMap(service -> service.resolve(user,start,end)).orElse(null);
        if(!cashOnly)inputs.ifPresent(service -> service.requireHourlyAbsenceReview(user,start,end,resolved.terms(),periodInput));
        var gross = cashOnly?new PayrollGrossCalculator.Result(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0):PayrollGrossCalculator.calculate(resolved.terms(), start, end, days, hours, payout, balance,periodInput==null?null:periodInput.data());
        var history = openingVersionId==null?ledger.before(user, resolved.terms(), start, provisional):ledger.before(user,resolved.terms(),start,provisional,openingVersionId);
        history.requireComplete();
        if(!cashOnly)gross = gross.withThirteenth(ledger.thirteenth(resolved.terms(), start, end, gross, history));
        var plan=payments==null?new PayrollPaymentLedgerService.Plan(List.of(),List.of()):payments.year(user,start);
        PayrollHistoricalAssessment assessment=null;
        if(historical!=null){var prepared=historical.prepare(user,t,start,end,history,plan);history=prepared.history();assessment=prepared.assessment();}
        if(cashOnly&&plan.payments().stream().noneMatch(i->!i.payment().receivedOn().isBefore(start)&&!i.payment().receivedOn().isAfter(end))
                &&ledger.originals(user).stream().noneMatch(p->p.getPeriodStart().equals(start)&&p.getCalculationSnapshot()!=null&&ledger.read(p).remuneration()!=null&&ledger.read(p).remuneration().cashOnly()))throw new IllegalStateException("Nach Austritt ist nur eine belegte zusätzliche Nachzahlungsperiode zulässig.");
        if(!plan.payments().isEmpty()||cashOnly||history.entries().stream().anyMatch(e->e.calculation().remuneration()!=null)) {
            if(plan.payments().stream().anyMatch(i->i.payment().receivedOn().isAfter(payrollToday())))throw new IllegalStateException("Eine zukünftige Zahlung ist noch kein bestätigter Zufluss.");
            PayrollPaymentLedgerService.requireHistory(plan,t,history);
            var applied=PayrollPaymentLedgerService.apply(plan,t,start,end,gross,cashOnly);
            var allocation=applied.remuneration().withAssessment(assessment);
            var deductions=tax.calculateAllocatedRun(user,applied.cash(),allocation,start,end,t,history,periodInput==null?null:periodInput.data());
            return new Calculation(resolved.versionId(),t,start,end,days,applied.cash(),deductions.getEmployee(),deductions.getEmployer(),deductions.getConfigurationReference(),deductions.getSwissBasis(),deductions.getGermanBasis(),history.references(),null,periodInput,allocation);
        }
        var deductions = periodInput==null?tax.calculateRun(user, gross, start, end, resolved.terms(), history):tax.calculateRun(user,gross,start,end,resolved.terms(),history,periodInput.data());
        return new Calculation(resolved.versionId(), resolved.terms(), start, end, days, gross, deductions.getEmployee(), deductions.getEmployer(), deductions.getConfigurationReference(), deductions.getSwissBasis(), deductions.getGermanBasis(), history.references(), null,periodInput);
    }
    public String snapshot(Calculation calculation) {
        try { return json.writeValueAsString(calculation); }
        catch (Exception e) { throw new IllegalStateException("Berechnungsgrundlagen konnten nicht gesichert werden.", e); }
    }
    public Calculation savedCalculation(Payslip slip){return ledger.read(slip);}
    public boolean financialOriginal(Payslip slip){return ledger.latest(slip).getCalculationSnapshot()!=null;}
    LocalDate payrollToday(){return LocalDate.now(ZoneId.of("Europe/Zurich"));}
    public Calculation applyYearAssignment(User user,Calculation c,LocalDate payoutDate,PayrollYearAssignment assignment,Map<Long,Payslip> provisional) {
        PayrollCorrectionService.validateCorrectionDate(c.periodStart(),payoutDate,assignment);
        if(assignment==null) return c;
        if(assignment.source()==null || assignment.source().isBlank() || assignment.source().strip().length()>250 || !Boolean.TRUE.equals(assignment.originalGrossAlreadyPaid()))
            throw new IllegalStateException("Berichtigungsgrundlage und Bestätigung, dass die Bruttolöhne bereits im Originaljahr bezahlt waren, sind erforderlich. Neue Nachzahlungen benötigen das Zuflussjahr.");
        if(payoutDate.getYear()==c.periodStart().getYear() || !"DE".equals(c.terms().country())) return c;
        if(!Boolean.TRUE.equals(assignment.germanCertificateNotTransmitted()) || !Boolean.TRUE.equals(assignment.germanAnnualAdjustmentPermitted()))
            throw new IllegalStateException("Vorjahresänderung: Noch nicht übermittelte Lohnsteuerbescheinigung und Zulässigkeit nach § 42b EStG ausdrücklich bestätigen.");
        LocalDate deadline=LocalDate.of(c.periodEnd().getYear()+1,2,1).withDayOfMonth(YearMonth.of(c.periodEnd().getYear()+1,2).lengthOfMonth());
        if(payoutDate.isAfter(deadline) || payrollToday().isAfter(deadline)) throw new IllegalStateException("Die Frist für den deutschen Jahresausgleich endet mit der letzten Februar-Abrechnung des Folgejahres.");
        if(!c.periodEnd().equals(c.periodEnd().withMonth(12).withDayOfMonth(31))) return c;
        Long openingId=c.correction()==null?null:c.correction().openingVersionId();
        var history=openingId==null?ledger.before(user,c.terms(),c.periodStart(),provisional):ledger.before(user,c.terms(),c.periodStart(),provisional,openingId); history.requireComplete();
        List<Calculation> year=new ArrayList<>(); history.entries().forEach(e->year.add(e.calculation().withGross(PayrollRemuneration.tax(e.calculation())))); year.add(c.withGross(PayrollRemuneration.tax(c)));
        var annual=GermanYearEndTax.calculate(year); Map<String,Double> employee=new LinkedHashMap<>(c.employee());
        for(var tax:annual.entrySet()) {
            BigDecimal paid=BigDecimal.ZERO;
            for(var entry:history.entries()) {
                for(var item:entry.calculation().employee().entrySet())if((item.getKey().equals(tax.getKey())||item.getKey().startsWith(tax.getKey()+" · "))&&!item.getKey().contains(" · Altperiode 2025-"))paid=paid.add(BigDecimal.valueOf(item.getValue()));
            }
            employee.keySet().removeIf(k->k.startsWith(tax.getKey()+" · ")&&!k.contains(" · Altperiode 2025-")); employee.put(tax.getKey(),tax.getValue().subtract(paid).doubleValue());
        }
        return new Calculation(c.rulesVersion(),c.terms(),c.periodStart(),c.periodEnd(),c.days(),c.gross(),Collections.unmodifiableMap(employee),c.employer(),
                c.taxReference()+"; §41c/§42b Jahresausgleich 2026: "+annual,c.swissBasis(),c.germanBasis(),c.history(),c.correction(),c.periodInputs(),c.remuneration());
    }
    private static void validateOvertimeContract(PayrollTerms rules, User historical, LocalDate date) {
        var agreed = rules.weeklyHours().multiply(java.math.BigDecimal.valueOf(60));
        if (Boolean.TRUE.equals(historical.getIsPercentage())) {
            // Compare contractual hours, not the sum of rounded daily time-account targets.
            // For example, 33% is 14.025 hours; rounding that contract to 14 changes its meaning.
            if (rules.weeklyHours().compareTo(WorkScheduleService.percentageBaseWeeklyHours(historical)) == 0) return;
        } else if (!historical.getWeeklySchedule().isEmpty() && historical.getScheduleCycle() != null
                && historical.getScheduleCycle() > 0 && (historical.getScheduleEffectiveDate() == null || !date.isBefore(historical.getScheduleEffectiveDate()))) {
            boolean matches = historical.getWeeklySchedule().stream().allMatch(week -> {
                int minutes = 0;
                for (DayOfWeek day : DayOfWeek.values()) minutes += (int) Math.round(week.getOrDefault(day.name().toLowerCase(java.util.Locale.ROOT), 0.0) * 60);
                return agreed.compareTo(java.math.BigDecimal.valueOf(minutes)) == 0;
            });
            if (matches) return;
        } else if (historical.getDailyWorkHours() != null && historical.getExpectedWorkDays() != null
                && agreed.compareTo(java.math.BigDecimal.valueOf(Math.round(historical.getDailyWorkHours() * 60) * historical.getExpectedWorkDays())) == 0) return;
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
            PayrollCorrectionService.validateCorrectionDate(slip.getPeriodStart(), slip.getPayoutDate(),saved.correction()==null?null:saved.correction().yearAssignment());
            original = ledger.originals(slip.getUser()).stream().filter(p -> p.getId().equals(slip.getCorrectionOfId())).findFirst().orElseThrow();
            previous = ledger.latest(original);
            if (saved.correction() == null || !ledger.reference(original, previous).equals(saved.correction().baseline())) throw new IllegalStateException("Die Korrekturbasis wurde inzwischen geändert. Korrekturlauf neu erstellen.");
            if (!Objects.equals(slip.getSupersedesCorrectionId(), previous.isCorrection() ? previous.getId() : null)) throw new IllegalStateException("Korrekturverkettung stimmt nicht mit dem letzten Beleg überein.");
            Double hours = saved.correction().replacementOvertimeHours();
            int restored = effectiveOvertimeMinutes(original, previous);
            current = calculate(slip.getUser(), slip.getPeriodStart(), slip.getPeriodEnd(), hours, hours != null && hours > 0, provisional, restored,saved.correction().openingVersionId()).withCorrection(saved.correction());
            current=applyYearAssignment(slip.getUser(),current,slip.getPayoutDate(),saved.correction().yearAssignment(),provisional);
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
        compareComponents(slip.getEarnings(), PayrollGrossCalculator.earnings(current.gross()));
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
