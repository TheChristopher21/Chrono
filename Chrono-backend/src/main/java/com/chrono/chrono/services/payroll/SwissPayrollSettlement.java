package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** KS45 annual reconciliation and 30/360 contribution ledger. All values are persisted per run. */
public final class SwissPayrollSettlement {
    private SwissPayrollSettlement() {}
    public record Basis(int insuranceDays, BigDecimal alvUvgBase, BigDecimal ktgBase,
                        BigDecimal annualRateIncome, BigDecimal monthlyRateIncome, BigDecimal ktgAnnualSalaryCap,PayrollKtgPool ktgPool,
                        List<SwissWithholdingSettlement.Allocation> withholdingAllocations,List<SwissInsuranceSettlement.Slice> insuranceSlices) {
        public Basis(int days,BigDecimal alv,BigDecimal ktg,BigDecimal annual,BigDecimal monthly,BigDecimal cap,PayrollKtgPool pool,List<SwissWithholdingSettlement.Allocation> allocations) {this(days,alv,ktg,annual,monthly,cap,pool,allocations,null);}
        public Basis(int insuranceDays,BigDecimal alvUvgBase,BigDecimal ktgBase,BigDecimal annualRateIncome,BigDecimal monthlyRateIncome,BigDecimal ktgAnnualSalaryCap,PayrollKtgPool pool) {
            this(insuranceDays,alvUvgBase,ktgBase,annualRateIncome,monthlyRateIncome,ktgAnnualSalaryCap,pool,null,null);
        }
        public Basis(int insuranceDays,BigDecimal alvUvgBase,BigDecimal ktgBase,BigDecimal annualRateIncome,BigDecimal monthlyRateIncome,BigDecimal ktgAnnualSalaryCap) {
            this(insuranceDays,alvUvgBase,ktgBase,annualRateIncome,monthlyRateIncome,ktgAnnualSalaryCap,null,null,null);
        }
        public Basis(int insuranceDays, BigDecimal alvUvgBase, BigDecimal ktgBase,
                BigDecimal annualRateIncome, BigDecimal monthlyRateIncome) {
            this(insuranceDays, alvUvgBase, ktgBase, annualRateIncome, monthlyRateIncome, null,null,null,null);
        }
    }
    public static TaxCalculationService.Result calculate(SwissSourceTax sourceTax, PayrollConfigurationService.Coverage coverage,
            PayrollTerms t, LocalDate start, LocalDate end, PayrollGrossCalculator.Result gross,
            PayrollLedgerService.History history) {
        return calculate(sourceTax,coverage,t,start,end,gross,history,null);
    }
    public static TaxCalculationService.Result calculate(SwissSourceTax sourceTax, PayrollConfigurationService.Coverage coverage,
            PayrollTerms t, LocalDate start, LocalDate end, PayrollGrossCalculator.Result gross,
            PayrollLedgerService.History history,PayrollPeriodData data) {
        return calculate(sourceTax,coverage,t,start,end,gross,history,data,true);
    }
    public static TaxCalculationService.Result calculate(SwissSourceTax sourceTax,PayrollConfigurationService.Coverage coverage,PayrollTerms t,LocalDate start,LocalDate end,
            PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data,boolean includeTax) {
        var insurance = coverage.insurance(); var pension = coverage.pension();
        BigDecimal total = gross.total();
        int days = PayrollPeriod.swissDays(start, end);
        BigDecimal ceiling = new BigDecimal("12350").multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(30), 12, RoundingMode.HALF_UP);
        BigDecimal ktgAnnual = insurance.getKtgAnnualSalaryCap();
        PayrollKtgPool ktgPool=insurance.getKtgPoolKey()==null?null:new PayrollKtgPool(insurance.getKtgPoolKey(),insurance.getKtgPoolFrom(),insurance.getKtgPoolThrough());
        if(ktgPool!=null && (ktgPool.from()==null || ktgPool.through()==null || start.isBefore(ktgPool.from()) || end.isAfter(ktgPool.through()))) throw new IllegalStateException("Die KTG-Police deckt den Abrechnungszeitraum nicht vollständig ab.");
        BigDecimal ktgCeiling = ktgAnnual.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_UP);
        BigDecimal insured = total.min(ceiling), ktg = ktgAnnual.signum() == 0 ? total : total.min(ktgCeiling);
        boolean ledgerAvailable = history.complete() && history.entries().stream().allMatch(e -> e.calculation().swissBasis() != null);
        if (ledgerAvailable) {
            BigDecimal yearGross = total, previousInsured = BigDecimal.ZERO;
            int yearDays = days;
            for (var entry : history.entries()) {
                var c = entry.calculation();
                yearGross = yearGross.add(c.gross().total()); yearDays += c.swissBasis().insuranceDays();
                previousInsured = previousInsured.add(c.swissBasis().alvUvgBase());
            }
            insured = yearGross.min(new BigDecimal("148200").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_UP)).subtract(previousInsured);
            if (insured.signum() < 0) throw new IllegalStateException("Die bisherigen Versicherungsgrundlagen müssen zuerst chronologisch rückgerechnet werden.");
        } else if ((total.compareTo(ceiling) > 0 && (t.hourly() || gross.thirteenthAmount().signum() > 0 || gross.payout().signum() > 0))
                || ktgAnnual.signum() > 0 && total.compareTo(ktgCeiling) > 0) {
            throw new IllegalStateException("Für die kumulativen Versicherungsgrenzen fehlen freigegebene Jahresgrundlagen. Frühere Monate zuerst rückrechnen.");
        }
        ktg=ktgBase(history,ktgAnnual,ktgPool,insurance.getId(),start,end,total,days);
        boolean liable = start.getYear() > t.birthDate().getYear() + 17;
        BigDecimal annualEquivalent = gross.periodicBase().multiply(BigDecimal.valueOf(360)).divide(BigDecimal.valueOf(days), 12, RoundingMode.HALF_UP);
        if (liable && total.signum() > 0 && annualEquivalent.compareTo(new BigDecimal("2500")) <= 0) throw new IllegalStateException("Geringfügiger Jahreslohn: Beitragspflicht muss ausdrücklich geklärt werden.");
        Map<String, Double> employee = new LinkedHashMap<>(), employer = new LinkedHashMap<>();
        String reference = "Versicherungsversion #" + insurance.getId() + "; BVG-Version #" + pension.getId() + "; CH "+start.getYear();
        var withholding=includeTax?SwissWithholdingSettlement.calculate(sourceTax,t,start,end,gross,history,data):new SwissWithholdingSettlement.Result(BigDecimal.ZERO,null,null,List.of());
        BigDecimal annualRate=withholding.annualRate(),monthlyRate=withholding.monthlyRate();
        put(employee,"Withholding tax",withholding.amount());
        for(var allocation:withholding.allocations()) reference+="; QST "+allocation.canton()+" ab "+allocation.from()+": "+allocation.amount().toPlainString()+"; Satzbasis "+allocation.rateIncome().toPlainString();
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
        if(ktgPool!=null) reference+="; KTG-Kreis "+ktgPool.key()+" ("+ktgPool.from()+"–"+ktgPool.through()+")";
        return new TaxCalculationService.Result(employee, employer, reference, new Basis(days, insured, ktg, annualRate, monthlyRate, ktgAnnual,ktgPool,withholding.allocations()));
    }
    /** KS45 7.3: known salary forecast; 13th entitlement included from the outset, no artificial monthly payout. */
    public static BigDecimal annualIncome(PayrollTerms t, LocalDate start, LocalDate end, PayrollGrossCalculator.Result current, PayrollLedgerService.History history) {
        return SwissWithholdingSettlement.annualIncome(t,start,end,current,history);
    }
    static BigDecimal futureSalary(PayrollTerms terms, LocalDate from, LocalDate until) {
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate cursor = from; !cursor.isAfter(until);) {
            LocalDate end = cursor.withDayOfMonth(cursor.lengthOfMonth());
            if (end.isAfter(until)) end = until;
            total = total.add(PayrollPeriod.prorate(terms.amount(), cursor, end,
                    terms.contract() == null ? null : terms.contract().salaryProration()));
            cursor = end.plusDays(1);
        }
        return total;
    }
    private static BigDecimal ktgBase(PayrollLedgerService.History history, BigDecimal currentCap,PayrollKtgPool pool,
            Long insuranceId,LocalDate start,LocalDate end,BigDecimal currentGross,int currentDays) {
        BigDecimal gross=currentGross,previousBase=BigDecimal.ZERO; int days=currentDays;
        for (var entry : history.entries()) {
            var previous = entry.calculation();
            if(pool!=null && previous.periodEnd().isBefore(pool.from())) continue;
            if(pool!=null && previous.periodStart().isBefore(pool.from())) throw new IllegalStateException("Der KTG-Vertragsbeginn schneidet eine frühere Abrechnungsperiode. Diese Zuordnung muss aufgeteilt werden.");
            PayrollKtgPool priorPool=previous.swissBasis()==null?null:previous.swissBasis().ktgPool();
            if(priorPool!=null && !Objects.equals(pool,priorPool)) throw new IllegalStateException("KTG-Abrechnungskreis/-Zeitraum widerspricht früheren Monaten. Einen neuen Kreis ab dem Vertragswechsel erfassen.");
            BigDecimal previousCap = previous.swissBasis() == null ? null : previous.swissBasis().ktgAnnualSalaryCap();
            // Older snapshots identify the immutable insurance version but do not store the cap separately.
            if (previousCap == null && insuranceId != null && previous.taxReference() != null
                    && previous.taxReference().startsWith("Versicherungsversion #" + insuranceId + ";")) previousCap = currentCap;
            if (previousCap == null) throw new IllegalStateException("Für frühere Monate fehlt die gesicherte KTG-Jahreslohngrenze. Diese Monate zuerst chronologisch über Korrekturbelege übernehmen; bestehende Abrechnungen bleiben erhalten.");
            if (previousCap.compareTo(currentCap) != 0) throw new IllegalStateException("Die KTG-Jahreslohngrenze ändert im selben Abrechnungskreis. Für den neuen Vertrag einen getrennten Kreis mit Vertragsbeginn/-ende erfassen.");
            if(previous.swissBasis()==null) throw new IllegalStateException("KTG-Vorperiodenbasis fehlt.");
            gross=gross.add(previous.gross().total()); previousBase=previousBase.add(previous.swissBasis().ktgBase()); days+=previous.swissBasis().insuranceDays();
        }
        if(currentCap.signum()==0) return currentGross;
        BigDecimal limit=currentCap.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360),12,RoundingMode.HALF_UP);
        BigDecimal base=gross.min(limit).subtract(previousBase);
        if(base.signum()<0) throw new IllegalStateException("Die bisherigen KTG-Basen dieses Kreises müssen zuerst chronologisch abgeglichen werden.");
        return base;
    }
    private static void fixed(Map<String, Double> target, String name, BigDecimal value) { if (value.signum() != 0) target.put(name, value.doubleValue()); }
    private static void put(Map<String, Double> target, String name, BigDecimal value) { fixed(target, name, value.divide(new BigDecimal("0.05"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("0.05"))); }
}
