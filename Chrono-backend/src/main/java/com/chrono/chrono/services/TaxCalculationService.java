package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.payroll.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.math.*;
import java.util.*;

@Service
public class TaxCalculationService {
    private final PayrollConfigurationService configuration;
    private final PayrollTermsService terms;
    private final SwissSourceTax sourceTax;
    public TaxCalculationService(PayrollConfigurationService configuration, PayrollTermsService terms, SwissSourceTax sourceTax) {
        this.configuration = configuration; this.terms = terms; this.sourceTax = sourceTax;
    }
    public static class Result {
        private final Map<String, Double> employee, employer;
        private final String configurationReference;
        private final SwissPayrollSettlement.Basis swissBasis;
        private GermanPayrollTax.Basis germanBasis;
        public Result(Map<String, Double> employee, Map<String, Double> employer) { this(employee, employer, null); }
        public Result(Map<String, Double> employee, Map<String, Double> employer, String reference) {
            this(employee, employer, reference, null);
        }
        public Result(Map<String, Double> employee, Map<String, Double> employer, String reference, SwissPayrollSettlement.Basis basis) {
            this.employee = Collections.unmodifiableMap(new LinkedHashMap<>(employee));
            this.employer = Collections.unmodifiableMap(new LinkedHashMap<>(employer)); this.configurationReference = reference; this.swissBasis = basis;
        }
        public SwissPayrollSettlement.Basis getSwissBasis() { return swissBasis; }
        public GermanPayrollTax.Basis getGermanBasis() { return germanBasis; }
        public Result withGermanBasis(GermanPayrollTax.Basis value) { this.germanBasis = value; return this; }
        public String getConfigurationReference() { return configurationReference; }
        public Map<String, Double> getEmployee() { return employee; }
        public Map<String, Double> getEmployer() { return employer; }
        public double getEmployeeTotal() { return total(employee); }
        public double getEmployerTotal() { return total(employer); }
        private double total(Map<String, Double> values) { return values.values().stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue(); }
    }
    public Result calculate(User user, double gross, LocalDate start, LocalDate end) {
        if (!Double.isFinite(gross) || gross < 0) throw new IllegalArgumentException("Ungültiger Bruttolohn.");
        return calculate(user, BigDecimal.valueOf(gross), start, end, terms.resolve(user, start, end).terms());
    }
    public Result calculate(User user, BigDecimal gross, LocalDate start, LocalDate end, PayrollTerms t) {
        validateSupportedPeriod(t, start, end);
        if (gross == null || gross.signum() < 0 || gross.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Bruttolohn muss ein positiver Centbetrag sein.");
        return "CH".equals(t.country()) ? swiss(user, gross, start, end, t) : GermanPayrollTax.calculate(t, gross, start, end);
    }
    public static void validateSupportedPeriod(PayrollTerms t, LocalDate start, LocalDate end) {
        validateEmploymentPeriod(t,start,end,2026);
    }
    public static void validateEmploymentPeriod(PayrollTerms t,LocalDate start,LocalDate end,int year) {
        PayrollReviewService.validatePeriod(start, end);
        PayrollTermsService.validate(t);
        if (start.getYear() != year) throw new IllegalStateException("Für diesen Zeitraum fehlen die hinterlegten Jahresregeln.");
        if (!java.time.YearMonth.from(start).equals(java.time.YearMonth.from(end))) throw new IllegalStateException("Jede Abrechnung muss innerhalb eines Kalendermonats liegen.");
        LocalDate expectedStart = t.entryDate().isAfter(start.withDayOfMonth(1)) ? t.entryDate() : start.withDayOfMonth(1);
        LocalDate monthEnd = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate expectedEnd = t.exitDate() != null && t.exitDate().isBefore(monthEnd) ? t.exitDate() : monthEnd;
        if (!start.equals(expectedStart) || !end.equals(expectedEnd)) throw new IllegalStateException("Der Zeitraum muss den Beschäftigungstagen des Monats entsprechen: " + expectedStart + " bis " + expectedEnd + ". Eintritt/Austritt in den Lohnregeln prüfen.");
        if (!PayrollPeriod.fullMonth(start, end) && t.contract() == null) throw new IllegalStateException("Für Teilmonate fehlen vertragliche Lohn- und BVG-Kürzungsregeln.");
        if (!t.birthDate().plusYears(64).isAfter(end)) throw new IllegalStateException("Referenzalter, Freibeträge und Rentenstatus sind noch nicht eingerichtet. Keine automatische Altersannahme.");
    }
    public static void validatePostEmploymentPeriod(PayrollTerms t,LocalDate start,LocalDate end) {
        PayrollReviewService.validatePeriod(start,end);PayrollTermsService.validate(t);
        if(start.getYear()!=2026||t.exitDate()==null||!start.isAfter(t.exitDate())||t.exitDate().getYear()<2025||!t.birthDate().plusYears(64).isAfter(end)
                ||!end.equals(start.withDayOfMonth(start.lengthOfMonth()))||start.getDayOfMonth()!=1&&!start.equals(t.exitDate().plusDays(1)))throw new IllegalStateException("Zusätzliche Nachzahlungsperioden benötigen Austritt und tatsächlichen Zufluss 2026. Die Periode beginnt am Folgetag des Austritts oder am Monatsanfang und endet am Monatsende.");
    }
    public Result calculateAllocatedRun(User user,PayrollGrossCalculator.Result cash,PayrollRemuneration allocation,LocalDate start,LocalDate end,PayrollTerms t,PayrollLedgerService.History history,PayrollPeriodData data) {
        var taxHistory=domain(history,true);var socialHistory=domain(history,false);
        if(!"CH".equals(t.country())) {
            if(allocation.assessment()!=null&&allocation.assessment().priorSocial()!=null) {
                var p=allocation.assessment().priorSocial();
                BigDecimal rawRv=BigDecimal.ZERO,rawKv=BigDecimal.ZERO;int rawDays=0;
                for(var c:allocation.assessment().originalPeriods())if(c.periodStart().getYear()==2025){rawRv=rawRv.add(c.germanBasis().pensionBase());rawKv=rawKv.add(c.germanBasis().healthBase());rawDays+=c.germanBasis().insuranceDays();}
                for(var ref:allocation.applied())if(ref.priorSocial()!=null){var supplied=ref.priorSocial();if(!supplied.assignmentDate().equals(p.assignmentDate())||supplied.insuranceDays()!=rawDays||supplied.pensionAssessed().compareTo(rawRv)!=0||supplied.healthAssessed().compareTo(rawKv)!=0||supplied.healthAdditionalRate().compareTo(p.healthAdditionalRate())!=0||!supplied.parent().equals(p.parent())||!supplied.childrenUnder25().equals(p.childrenUnder25())||!supplied.saxony().equals(p.saxony()))throw new IllegalStateException("Die ausdrücklich erfasste Vorjahres-SV widerspricht den bestätigten Original-Monatsgrundlagen 2025.");}
                data=data==null?new PayrollPeriodData(List.of(),null,null,null,null,p):new PayrollPeriodData(data.absences(),data.germanConcurrentIncome(),data.swissOtherAperiodicGross(),data.swissInsuranceAllocations(),data.payments(),p,data.historicalBaseline(),data.historicalInsuranceAllocations());
            }
            var social=allocation.cashOnly()?new Result(Map.of(),Map.of()).withGermanBasis(new GermanPayrollTax.Basis(0,BigDecimal.ZERO,BigDecimal.ZERO,null))
                    :GermanSocialPayments.calculate(t,cash,allocation,start,end,socialHistory,data);
            var withholding=GermanPayrollTax.taxes(t,allocation.taxGross(),start,end,taxHistory,oneOff(t,cash,allocation.taxGross()));
            Map<String,Double> employee=new LinkedHashMap<>(social.getEmployee());employee.keySet().removeIf(TaxCalculationService::germanTax);withholding.forEach((k,v)->employee.put(k.replace(" · thirteenth"," · one-off remuneration"),v));
            Map<String,Double> employer=new LinkedHashMap<>(social.getEmployer());historicalCharges(allocation,employee,employer);
            return new Result(employee,employer,"BMF 2026; Nachzahlung: getrennte Zufluss-/Steuer-/SV-Perioden").withGermanBasis(social.getGermanBasis());
        }
        Result social;
        if(allocation.cashOnly())social=new Result(Map.of(),Map.of(),"CH 2026; zusätzliche Zahlung nach Austritt",new SwissPayrollSettlement.Basis(0,BigDecimal.ZERO,BigDecimal.ZERO,null,null));
        else {
            var coverage=configuration.resolve(user,start,end);if(!coverage.insuranceComplete()||!coverage.pensionComplete())throw new IllegalStateException("Versicherungswerte/BVG der SV-Periode fehlen.");
            social=coverage.insuranceChangesWithinPeriod()||coverage.pensionChangesWithinPeriod()||coverage.insurance().getUvgPoolKey()!=null||data!=null&&data.swissInsuranceAllocations()!=null||socialHistory.entries().stream().anyMatch(e->e.calculation().swissBasis()!=null&&e.calculation().swissBasis().insuranceSlices()!=null)
                    ?SwissInsuranceSettlement.calculate(sourceTax,coverage,t,start,end,allocation.socialGross(),socialHistory,data,false)
                    :SwissPayrollSettlement.calculate(sourceTax,coverage,t,start,end,allocation.socialGross(),socialHistory,data,false);
        }
        if(allocation.cashOnly()&&allocation.assessment()!=null&&taxHistory.entries().stream().noneMatch(e->e.calculation().swissBasis()!=null&&e.calculation().swissBasis().insuranceDays()>0)) {
            taxHistory=new PayrollLedgerService.History(allocation.assessment().originalPeriods().stream().filter(c->c.periodStart().getYear()==2025).map(c->new PayrollLedgerService.Entry(new com.chrono.chrono.entities.Payslip(),new com.chrono.chrono.entities.Payslip(),c)).toList(),taxHistory.references(),true);
        }
        var withheld=allocation.cashOnly()?postEmploymentSwissTax(t,start,allocation.taxGross(),taxHistory,data):SwissWithholdingSettlement.calculate(sourceTax,t,start,end,allocation.taxGross(),taxHistory,data);
        Map<String,Double> employee=new LinkedHashMap<>(social.getEmployee());if(withheld.amount().signum()!=0)employee.put("Withholding tax",withheld.amount().doubleValue());
        Map<String,Double> employer=new LinkedHashMap<>(social.getEmployer());historicalCharges(allocation,employee,employer);
        var b=social.getSwissBasis();return new Result(employee,employer,social.getConfigurationReference()+"; Nachzahlung: getrennte Zufluss-/Steuer-/SV-Perioden",
                new SwissPayrollSettlement.Basis(b.insuranceDays(),b.alvUvgBase(),b.ktgBase(),withheld.annualRate(),withheld.monthlyRate(),b.ktgAnnualSalaryCap(),b.ktgPool(),withheld.allocations(),b.insuranceSlices()));
    }
    private static void historicalCharges(PayrollRemuneration a,Map<String,Double> employee,Map<String,Double> employer) {
        if(a.assessment()!=null){a.assessment().employee().forEach((k,v)->employee.merge(k,v,Double::sum));a.assessment().employer().forEach((k,v)->employer.merge(k,v,Double::sum));}
    }
    private static BigDecimal oneOff(PayrollTerms t,PayrollGrossCalculator.Result cash,PayrollGrossCalculator.Result allocated) {return (t.thirteenthMonths()==1?BigDecimal.ZERO:cash.thirteenthAmount()).add(allocated.thirteenthAmount().subtract(cash.thirteenthAmount()));}
    private static boolean germanTax(String key){return key.startsWith("Income tax")||key.startsWith("Solidarity surcharge")||key.startsWith("Church tax");}
    public static PayrollLedgerService.History domain(PayrollLedgerService.History h,boolean tax) {
        return new PayrollLedgerService.History(h.entries().stream().map(e->new PayrollLedgerService.Entry(e.original(),e.revision(),e.calculation().withGross(tax?PayrollRemuneration.tax(e.calculation()):PayrollRemuneration.social(e.calculation())))).toList(),h.references(),h.complete());
    }
    private SwissWithholdingSettlement.Result postEmploymentSwissTax(PayrollTerms t,LocalDate start,PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data) {
        if(!"CH_SOURCE".equals(t.taxMode())||gross.total().signum()==0)return new SwissWithholdingSettlement.Result(BigDecimal.ZERO,null,null,List.of());
        var normal=history.entries().stream().filter(e->e.calculation().swissBasis()!=null&&e.calculation().swissBasis().insuranceDays()>0).toList();
        if(normal.isEmpty())throw new IllegalStateException("Für die Nachzahlung nach Austritt fehlt die letzte gesicherte Lohn-/Quellensteuerbasis.");
        var last=normal.get(normal.size()-1).calculation();boolean annual=SwissSourceTax.annualModel(t.canton());BigDecimal periodic;
        if(annual) {
            periodic=last.swissBasis().annualRateIncome();
            if(periodic==null)periodic=SwissPayrollSettlement.annualIncome(last.terms(),last.periodStart(),last.periodEnd(),last.gross(),new PayrollLedgerService.History(normal.subList(0,normal.size()-1),history.references(),true));
            for(int index=normal.size()-1;index>=0;index--){var c=normal.get(index).calculation();if(!Objects.equals(c.terms().canton(),last.terms().canton()))break;periodic=periodic.subtract(c.gross().premium()).subtract(c.gross().payout());if(c.periodInputs()!=null&&c.periodInputs().data().swissOtherAperiodicGross()!=null)periodic=periodic.subtract(c.periodInputs().data().swissOtherAperiodicGross());}
        } else {
            periodic=last.swissBasis().monthlyRateIncome();if(periodic==null)periodic=last.gross().periodicBase().multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(PayrollPeriod.swissDays(last.periodStart(),last.periodEnd())),12,RoundingMode.HALF_UP);
            else {periodic=periodic.subtract(last.gross().premium()).subtract(last.gross().payout());if(last.periodInputs()!=null&&last.periodInputs().data().swissOtherAperiodicGross()!=null)periodic=periodic.subtract(last.periodInputs().data().swissOtherAperiodicGross());}
        }
        BigDecimal other=data==null||data.swissOtherAperiodicGross()==null?BigDecimal.ZERO:data.swissOtherAperiodicGross();BigDecimal rate=periodic.add(gross.total()).add(other);
        var tax=annual?sourceTax.annual(2026,t.canton(),t.tariffCode(),gross.total(),rate):sourceTax.monthly(2026,t.canton(),t.tariffCode(),gross.total(),rate);
        return new SwissWithholdingSettlement.Result(tax.amount(),annual?rate:null,annual?null:rate,List.of(new SwissWithholdingSettlement.Allocation("POST_EXIT:"+t.canton()+":"+start,t.canton(),start,tax.amount(),rate,gross.total())));
    }
    public Result calculateRun(User user, PayrollGrossCalculator.Result gross, LocalDate start, LocalDate end,
            PayrollTerms t, PayrollLedgerService.History history) {
        return calculateRun(user,gross,start,end,t,history,null);
    }
    public Result calculateRun(User user, PayrollGrossCalculator.Result gross, LocalDate start, LocalDate end,
            PayrollTerms t, PayrollLedgerService.History history,PayrollPeriodData periodData) {
        validateSupportedPeriod(t, start, end);
        if (!"CH".equals(t.country())) return GermanPayrollTax.calculateRun(t, gross, start, end, history,periodData);
        var coverage = configuration.resolve(user, start, end);
        if (!coverage.insuranceComplete() || !coverage.pensionComplete()) throw new IllegalStateException("Gültige Versicherungswerte oder persönliche BVG-Beiträge fehlen.");
        if (coverage.insuranceChangesWithinPeriod() || coverage.pensionChangesWithinPeriod()||coverage.insurance().getUvgPoolKey()!=null
                ||periodData!=null&&periodData.swissInsuranceAllocations()!=null||history.entries().stream().anyMatch(e->e.calculation().swissBasis()!=null&&e.calculation().swissBasis().insuranceSlices()!=null))
            return SwissInsuranceSettlement.calculate(sourceTax,coverage,t,start,end,gross,history,periodData);
        return SwissPayrollSettlement.calculate(sourceTax, coverage, t, start, end, gross, history,periodData);
    }
    private Result swiss(User user, BigDecimal gross, LocalDate start, LocalDate end, PayrollTerms t) {
        var coverage = configuration.resolve(user, start, end);
        if (!coverage.insuranceComplete() || !coverage.pensionComplete()) throw new IllegalStateException("Gültige Versicherungswerte oder persönliche BVG-Beiträge fehlen. Bitte in den Firmenparametern ergänzen.");
        if (coverage.insuranceChangesWithinPeriod() || coverage.pensionChangesWithinPeriod()) throw new IllegalStateException("Versicherungswerte oder BVG ändern innerhalb des Monats. Getrennte Berechnung erforderlich.");
        var insurance = coverage.insurance(); var pension = coverage.pension();
        boolean liable = start.getYear() > t.birthDate().getYear() + 17;
        if (liable && gross.signum() > 0 && gross.multiply(BigDecimal.valueOf(12)).compareTo(new BigDecimal("2500")) <= 0) throw new IllegalStateException("Geringfügiger Jahreslohn: Beitragspflicht und Jahreslohn müssen gesondert geklärt werden.");
        BigDecimal cap = new BigDecimal("12350");
        if (gross.compareTo(cap) > 0 && (t.hourly() || t.amount().compareTo(cap) < 0)) throw new IllegalStateException("Schwankender Lohn über der ALV-/UVG-Grenze benötigt eine geprüfte kumulative Jahresbasis.");
        BigDecimal insured = gross.min(cap);
        BigDecimal ktgCap = insurance.getKtgAnnualSalaryCap().divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        if (ktgCap.signum() > 0 && gross.compareTo(ktgCap) > 0 && (t.hourly() || t.amount().compareTo(ktgCap) < 0)) throw new IllegalStateException("Schwankender Lohn über der KTG-Grenze benötigt eine geprüfte kumulative Jahresbasis.");
        BigDecimal ktgBase = ktgCap.signum() == 0 ? gross : gross.min(ktgCap);
        Map<String, Double> employee = new LinkedHashMap<>(), employer = new LinkedHashMap<>();
        String reference = "Versicherungsversion #" + insurance.getId() + "; BVG-Version #" + pension.getId() + "; CH 2026";
        if ("CH_SOURCE".equals(t.taxMode())) {
            var tax = sourceTax.monthly(start.getYear(), t.canton(), t.tariffCode(), gross, gross);
            put(employee, "Withholding tax", tax.amount()); reference += "; " + tax.reference();
        }
        // Ordinary Swiss income tax is assessed separately, never a second payroll deduction.
        BigDecimal ahv = liable ? gross.multiply(new BigDecimal("0.053")) : BigDecimal.ZERO;
        BigDecimal alv = liable ? insured.multiply(new BigDecimal("0.011")) : BigDecimal.ZERO;
        put(employee, "AHV/IV/EO", ahv); put(employer, "AHV/IV/EO", ahv);
        put(employee, "ALV", alv); put(employer, "ALV", alv);
        // Fixed pension amounts are contractual inputs, not a percentage calculation.
        if (pension.getBvgEmployee().signum() > 0) employee.put("BVG", pension.getBvgEmployee().doubleValue());
        if (pension.getBvgEmployer().signum() > 0) employer.put("BVG", pension.getBvgEmployer().doubleValue());
        if (t.weeklyHours().compareTo(new BigDecimal("8")) >= 0) put(employee, "NBU", insured.multiply(insurance.getUvgNbuRate()));
        put(employer, "BU", insured.multiply(insurance.getUvgBuRate()));
        put(employee, "KTG", ktgBase.multiply(insurance.getKtgRateEmployee()));
        put(employer, "KTG", ktgBase.multiply(insurance.getKtgRateEmployer()));
        put(employee, "FAK", (liable ? gross : BigDecimal.ZERO).multiply(insurance.getFakEmployeeRate()));
        put(employer, "FAK", (liable ? gross : BigDecimal.ZERO).multiply(insurance.getFakRate()));
        put(employer, "AHV administration", ahv.multiply(BigDecimal.valueOf(2)).multiply(insurance.getAhvAdminRate()));
        return new Result(employee, employer, reference);
    }
    private static void put(Map<String, Double> map, String name, BigDecimal value) {
        // Swissdec payroll calculation guideline: commercial rounding to CHF 0.05.
        BigDecimal amount = value.divide(new BigDecimal("0.05"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("0.05"));
        if (amount.signum() != 0) map.put(name, amount.doubleValue());
    }
}
