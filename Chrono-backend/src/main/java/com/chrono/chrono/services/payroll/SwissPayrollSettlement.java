package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** KS45 annual reconciliation and 30/360 contribution ledger. All values are persisted per run. */
public final class SwissPayrollSettlement {
    private SwissPayrollSettlement() {}
    public record Basis(int insuranceDays, BigDecimal alvUvgBase, BigDecimal ktgBase,
                        BigDecimal annualRateIncome, BigDecimal monthlyRateIncome) {}
    public static TaxCalculationService.Result calculate(SwissSourceTax sourceTax, PayrollConfigurationService.Coverage coverage,
            PayrollTerms t, LocalDate start, LocalDate end, PayrollGrossCalculator.Result gross,
            PayrollLedgerService.History history) {
        var insurance = coverage.insurance(); var pension = coverage.pension();
        BigDecimal total = gross.total();
        int days = PayrollPeriod.swissDays(start, end);
        BigDecimal ceiling = new BigDecimal("12350").multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(30), 12, RoundingMode.HALF_UP);
        BigDecimal ktgAnnual = insurance.getKtgAnnualSalaryCap();
        BigDecimal ktgCeiling = ktgAnnual.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_UP);
        BigDecimal insured = total.min(ceiling), ktg = ktgAnnual.signum() == 0 ? total : total.min(ktgCeiling);
        boolean ledgerAvailable = history.complete() && history.entries().stream().allMatch(e -> e.calculation().swissBasis() != null);
        if (ledgerAvailable) {
            BigDecimal yearGross = total, previousInsured = BigDecimal.ZERO, previousKtg = BigDecimal.ZERO;
            int yearDays = days;
            for (var entry : history.entries()) {
                var c = entry.calculation();
                yearGross = yearGross.add(c.gross().total()); yearDays += c.swissBasis().insuranceDays();
                previousInsured = previousInsured.add(c.swissBasis().alvUvgBase());
                previousKtg = previousKtg.add(c.swissBasis().ktgBase());
            }
            insured = yearGross.min(new BigDecimal("148200").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_UP)).subtract(previousInsured);
            ktg = ktgAnnual.signum() == 0 ? total : yearGross.min(ktgAnnual.multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_UP)).subtract(previousKtg);
            if (insured.signum() < 0 || ktg.signum() < 0) throw new IllegalStateException("Die bisherigen Versicherungsgrundlagen müssen zuerst chronologisch rückgerechnet werden.");
        } else if ((total.compareTo(ceiling) > 0 && (t.hourly() || gross.thirteenthAmount().signum() > 0 || gross.payout().signum() > 0))
                || ktgAnnual.signum() > 0 && total.compareTo(ktgCeiling) > 0) {
            throw new IllegalStateException("Für die kumulativen Versicherungsgrenzen fehlen freigegebene Jahresgrundlagen. Frühere Monate zuerst rückrechnen.");
        }
        boolean liable = start.getYear() > t.birthDate().getYear() + 17;
        BigDecimal annualEquivalent = gross.base().multiply(BigDecimal.valueOf(360)).divide(BigDecimal.valueOf(days), 12, RoundingMode.HALF_UP);
        if (liable && total.signum() > 0 && annualEquivalent.compareTo(new BigDecimal("2500")) <= 0) throw new IllegalStateException("Geringfügiger Jahreslohn: Beitragspflicht muss ausdrücklich geklärt werden.");
        Map<String, Double> employee = new LinkedHashMap<>(), employer = new LinkedHashMap<>();
        String reference = "Versicherungsversion #" + insurance.getId() + "; BVG-Version #" + pension.getId() + "; CH 2026";
        BigDecimal annualRate = null, monthlyRate = null;
        if ("CH_SOURCE".equals(t.taxMode())) {
            if (SwissSourceTax.annualModel(t.canton())) {
                history.requireComplete();
                annualRate = annualIncome(t, start, end, gross, history);
                BigDecimal due = sourceTax.annual(start.getYear(), t.canton(), t.tariffCode(), total, annualRate).amount();
                for (var entry : history.entries()) {
                    var previous = entry.calculation();
                    if (!"CH_SOURCE".equals(previous.terms().taxMode()) || !t.canton().equals(previous.terms().canton())) throw new IllegalStateException("Wechsel des Quellensteuerkantons/-status benötigt eine getrennte Jahreszuordnung.");
                    due = due.add(sourceTax.annual(start.getYear(), t.canton(), previous.terms().tariffCode(), previous.gross().total(), annualRate).amount())
                            .subtract(BigDecimal.valueOf(previous.employee().getOrDefault("Withholding tax", 0.0)));
                }
                put(employee, "Withholding tax", due);
                reference += "; KS45 Jahresausgleich; Satzbasis Jahr " + annualRate.toPlainString();
            } else {
                monthlyRate = gross.base().multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(days), 12, RoundingMode.HALF_UP)
                        .add(gross.premium()).add(gross.payout());
                if (gross.thirteenthAmount().signum() > 0) {
                    int eligibleDays = days;
                    for (int i = history.entries().size() - 1; i >= 0; i--) {
                        var previous = history.entries().get(i).calculation();
                        if (previous.gross().thirteenthAmount().signum() > 0) break;
                        if (previous.terms().thirteenthMonths() > 0) eligibleDays += PayrollPeriod.swissDays(previous.periodStart(), previous.periodEnd());
                    }
                    monthlyRate = monthlyRate.add(gross.thirteenthAmount().multiply(BigDecimal.valueOf(t.thirteenthMonths() * 30L)).divide(BigDecimal.valueOf(eligibleDays), 12, RoundingMode.HALF_UP));
                }
                var tax = sourceTax.monthly(start.getYear(), t.canton(), t.tariffCode(), total, monthlyRate);
                put(employee, "Withholding tax", tax.amount()); reference += "; " + tax.reference();
            }
        }
        BigDecimal ahv = liable ? total.multiply(new BigDecimal("0.053")) : BigDecimal.ZERO;
        BigDecimal alv = liable ? insured.multiply(new BigDecimal("0.011")) : BigDecimal.ZERO;
        put(employee, "AHV/IV/EO", ahv); put(employer, "AHV/IV/EO", ahv);
        put(employee, "ALV", alv); put(employer, "ALV", alv);
        String pensionMethod = t.contract() == null ? null : t.contract().pensionProration();
        fixed(employee, "BVG", PayrollPeriod.prorate(pension.getBvgEmployee(), start, end, pensionMethod));
        fixed(employer, "BVG", PayrollPeriod.prorate(pension.getBvgEmployer(), start, end, pensionMethod));
        if (t.weeklyHours().compareTo(new BigDecimal("8")) >= 0) put(employee, "NBU", insured.multiply(insurance.getUvgNbuRate()));
        put(employer, "BU", insured.multiply(insurance.getUvgBuRate()));
        put(employee, "KTG", ktg.multiply(insurance.getKtgRateEmployee())); put(employer, "KTG", ktg.multiply(insurance.getKtgRateEmployer()));
        put(employee, "FAK", (liable ? total : BigDecimal.ZERO).multiply(insurance.getFakEmployeeRate()));
        put(employer, "FAK", (liable ? total : BigDecimal.ZERO).multiply(insurance.getFakRate()));
        put(employer, "AHV administration", ahv.multiply(BigDecimal.valueOf(2)).multiply(insurance.getAhvAdminRate()));
        return new TaxCalculationService.Result(employee, employer, reference, new Basis(days, insured, ktg, annualRate, monthlyRate));
    }
    /** KS45 7.3: known salary forecast; 13th entitlement included from the outset, no artificial monthly payout. */
    public static BigDecimal annualIncome(PayrollTerms t, LocalDate start, LocalDate end, PayrollGrossCalculator.Result current, PayrollLedgerService.History history) {
        BigDecimal periodic = current.base(), aperiodic = current.payout().add(current.premium());
        BigDecimal thirteenthBase = t.thirteenthMonths() > 0 ? current.base() : BigDecimal.ZERO;
        int elapsed = PayrollPeriod.swissDays(start, end);
        for (var entry : history.entries()) {
            var c = entry.calculation(); periodic = periodic.add(c.gross().base());
            aperiodic = aperiodic.add(c.gross().payout()).add(c.gross().premium());
            if (c.terms().thirteenthMonths() > 0) thirteenthBase = thirteenthBase.add(c.gross().base());
            elapsed += PayrollPeriod.swissDays(c.periodStart(), c.periodEnd());
        }
        LocalDate until = t.exitDate() != null && t.exitDate().getYear() == end.getYear() ? t.exitDate() : end.withMonth(12).withDayOfMonth(31);
        int futureDays = PayrollPeriod.swissDays(end.plusDays(1), until);
        BigDecimal forecast = t.hourly() ? periodic.multiply(BigDecimal.valueOf(futureDays)).divide(BigDecimal.valueOf(elapsed), 12, RoundingMode.HALF_UP)
                : t.amount().multiply(BigDecimal.valueOf(futureDays)).divide(BigDecimal.valueOf(30), 12, RoundingMode.HALF_UP);
        BigDecimal thirteenth = thirteenthBase.add(t.thirteenthMonths() > 0 ? forecast : BigDecimal.ZERO).divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        return periodic.add(forecast).add(thirteenth).multiply(BigDecimal.valueOf(360)).divide(BigDecimal.valueOf(elapsed + futureDays), 12, RoundingMode.HALF_UP).add(aperiodic);
    }
    private static void fixed(Map<String, Double> target, String name, BigDecimal value) { if (value.signum() != 0) target.put(name, value.doubleValue()); }
    private static void put(Map<String, Double> target, String name, BigDecimal value) { fixed(target, name, value.divide(new BigDecimal("0.05"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("0.05"))); }
}
