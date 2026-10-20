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
        PayrollReviewService.validatePeriod(start, end);
        PayrollTermsService.validate(t);
        if (start.getYear() != 2026) throw new IllegalStateException("Automatische Abrechnung ist nur mit den hinterlegten Jahresregeln 2026 möglich.");
        if (!java.time.YearMonth.from(start).equals(java.time.YearMonth.from(end))) throw new IllegalStateException("Jede Abrechnung muss innerhalb eines Kalendermonats liegen.");
        LocalDate expectedStart = t.entryDate().isAfter(start.withDayOfMonth(1)) ? t.entryDate() : start.withDayOfMonth(1);
        LocalDate monthEnd = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate expectedEnd = t.exitDate() != null && t.exitDate().isBefore(monthEnd) ? t.exitDate() : monthEnd;
        if (!start.equals(expectedStart) || !end.equals(expectedEnd)) throw new IllegalStateException("Der Zeitraum muss den Beschäftigungstagen des Monats entsprechen: " + expectedStart + " bis " + expectedEnd + ". Eintritt/Austritt in den Lohnregeln prüfen.");
        if (!PayrollPeriod.fullMonth(start, end) && t.contract() == null) throw new IllegalStateException("Für Teilmonate fehlen vertragliche Lohn- und BVG-Kürzungsregeln.");
        if (!t.birthDate().plusYears(64).isAfter(end)) throw new IllegalStateException("Referenzalter, Freibeträge und Rentenstatus sind noch nicht eingerichtet. Keine automatische Altersannahme.");
    }
    public Result calculateRun(User user, PayrollGrossCalculator.Result gross, LocalDate start, LocalDate end,
            PayrollTerms t, PayrollLedgerService.History history) {
        validateSupportedPeriod(t, start, end);
        if (!"CH".equals(t.country())) return GermanPayrollTax.calculateRun(t, gross, start, end, history);
        var coverage = configuration.resolve(user, start, end);
        if (!coverage.insuranceComplete() || !coverage.pensionComplete()) throw new IllegalStateException("Gültige Versicherungswerte oder persönliche BVG-Beiträge fehlen.");
        if (coverage.insuranceChangesWithinPeriod() || coverage.pensionChangesWithinPeriod()) throw new IllegalStateException("Versicherungswerte ändern innerhalb des Abrechnungszeitraums.");
        return SwissPayrollSettlement.calculate(sourceTax, coverage, t, start, end, gross, history);
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
