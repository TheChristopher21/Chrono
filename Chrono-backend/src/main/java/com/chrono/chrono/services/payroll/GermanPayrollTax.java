package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.TaxCalculationService;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Regular, fully insured monthly employment. Other social-insurance statuses require separate rules. */
public final class GermanPayrollTax {
    private GermanPayrollTax() {}
    public record Basis(int insuranceDays, BigDecimal pensionBase, BigDecimal healthBase, BigDecimal projectedAnnualGross) {}
    public static TaxCalculationService.Result calculateRun(PayrollTerms t, PayrollGrossCalculator.Result gross, LocalDate start, LocalDate end,
            com.chrono.chrono.services.PayrollLedgerService.History history) {
        if (start.getYear() != 2026) throw new IllegalStateException("Deutsche Jahresparameter sind nur für 2026 installiert.");
        boolean full = PayrollPeriod.fullMonth(start, end);
        int calendarDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        int svDays = full ? 30 : Math.min(30, calendarDays);
        BigDecimal oneOff = t.thirteenthMonths() == 1 ? BigDecimal.ZERO : gross.thirteenthAmount();
        BigDecimal regular = gross.total().subtract(oneOff);
        BigDecimal regularMonthly = !t.hourly() ? t.amount() : regular.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(svDays), 12, RoundingMode.HALF_UP);
        if (regularMonthly.compareTo(new BigDecimal("2000")) <= 0) throw new IllegalStateException("Mini-/Midijobs benötigen eigene Sozialversicherungsregeln.");
        BigDecimal pensionRegular = regular.min(new BigDecimal("8450").multiply(BigDecimal.valueOf(svDays)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP));
        BigDecimal healthRegular = regular.min(new BigDecimal("5812.50").multiply(BigDecimal.valueOf(svDays)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP));
        BigDecimal pensionExtra = BigDecimal.ZERO, healthExtra = BigDecimal.ZERO, annual = null;
        if (oneOff.signum() > 0) {
            history.requireComplete();
            if (t.contract() == null || t.contract().germanPreviousEmploymentGross() == null) throw new IllegalStateException("Vorarbeitgeber-Jahresbrutto für sonstige Bezüge fehlt.");
            BigDecimal previousPension = BigDecimal.ZERO, previousHealth = BigDecimal.ZERO;
            annual = regular.add(t.contract().germanPreviousEmploymentGross());
            int yearDays = svDays;
            for (var entry : history.entries()) {
                var c = entry.calculation();
                if (c.germanBasis() == null) throw new IllegalStateException("Für Einmalzahlungen müssen die bisherigen SV-Jahresgrundlagen zuerst rückgerechnet werden.");
                previousPension = previousPension.add(c.germanBasis().pensionBase()); previousHealth = previousHealth.add(c.germanBasis().healthBase());
                yearDays += c.germanBasis().insuranceDays(); annual = annual.add(c.gross().total());
            }
            pensionExtra = oneOff.min(new BigDecimal("101400").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360), 2, RoundingMode.HALF_UP).subtract(previousPension).subtract(pensionRegular).max(BigDecimal.ZERO));
            healthExtra = oneOff.min(new BigDecimal("69750").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360), 2, RoundingMode.HALF_UP).subtract(previousHealth).subtract(healthRegular).max(BigDecimal.ZERO));
            if (end.getMonthValue() <= 3 && t.entryDate().isBefore(start.withDayOfYear(1)) && healthExtra.compareTo(oneOff) < 0)
                throw new IllegalStateException("Märzklausel: Die Einmalzahlung benötigt SV-Grundlagen des Vorjahres; ohne diese wird sie nicht freigegeben.");
            LocalDate until = t.exitDate() != null && t.exitDate().getYear() == end.getYear() ? t.exitDate() : end.withMonth(12).withDayOfMonth(31);
            // JRE4 includes the expected regular pay, including the contractual proration of a known exit month.
            // Swiss 30/360 tax days would produce a different forecast from CALENDAR_DAYS salary payments.
            for (LocalDate cursor = end.plusDays(1); !cursor.isAfter(until);) {
                LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
                LocalDate forecastEnd = monthEnd.isBefore(until) ? monthEnd : until;
                String proration = t.contract() == null ? null : t.contract().salaryProration();
                annual = annual.add(PayrollPeriod.prorate(regularMonthly, cursor, forecastEnd, proration));
                cursor = monthEnd.plusDays(1);
            }
        }
        boolean surcharge = !t.parent() && !start.isBefore(t.birthDate().plusYears(23).withDayOfMonth(1).plusMonths(1));
        int discounts = t.parent() ? Math.min(4, Math.max(0, t.childrenUnder25() - 1)) : 0;
        var pap = new BmfLohnsteuer2026();
        pap.LZZ = full ? 2 : 4;
        pap.RE4 = full ? regular.movePointRight(2) : regular.movePointRight(2).divide(BigDecimal.valueOf(calendarDays), 0, RoundingMode.DOWN);
        pap.STKL = t.taxClass(); pap.af = t.taxFactor().compareTo(BigDecimal.ONE) == 0 ? 0 : 1; pap.f = t.taxFactor().doubleValue();
        pap.ZKF = t.childAllowance(); pap.R = t.churchRate().signum() > 0 ? 1 : 0; pap.KVZ = t.healthAdditionalRate().movePointRight(2);
        pap.PVS = t.saxony() ? 1 : 0; pap.PVZ = surcharge ? 1 : 0; pap.PVA = BigDecimal.valueOf(discounts);
        pap.LZZFREIB = full ? t.taxAllowance().movePointRight(2) : t.taxAllowance().movePointRight(2).divide(BigDecimal.valueOf(30), 0, RoundingMode.DOWN);
        pap.LZZHINZU = full ? t.taxAddition().movePointRight(2) : t.taxAddition().movePointRight(2).divide(BigDecimal.valueOf(30), 0, RoundingMode.DOWN);
        if (oneOff.signum() > 0) {
            pap.SONSTB = oneOff.movePointRight(2); pap.JRE4 = annual.movePointRight(2);
            pap.JFREIB = t.taxAllowance().multiply(BigDecimal.valueOf(1200)); pap.JHINZU = t.taxAddition().multiply(BigDecimal.valueOf(1200));
        }
        pap.calculate(); BigDecimal multiplier = BigDecimal.valueOf(full ? 1 : calendarDays);
        Map<String, Double> employee = new LinkedHashMap<>(), employer = new LinkedHashMap<>();
        put(employee, "Income tax", pap.LSTLZZ.multiply(multiplier).movePointLeft(2));
        put(employee, "Solidarity surcharge", pap.SOLZLZZ.multiply(multiplier).movePointLeft(2));
        put(employee, "Church tax", pap.BK.multiply(multiplier).movePointLeft(2).multiply(t.churchRate()));
        if (oneOff.signum() > 0) {
            put(employee, "Income tax · thirteenth", pap.STS.movePointLeft(2)); put(employee, "Solidarity surcharge · thirteenth", pap.SOLZS.movePointLeft(2));
            put(employee, "Church tax · thirteenth", pap.BKS.movePointLeft(2).multiply(t.churchRate()));
        }
        BigDecimal kvRate = new BigDecimal("0.073").add(t.healthAdditionalRate().divide(BigDecimal.valueOf(2)));
        BigDecimal pvEmployee = new BigDecimal(t.saxony() ? "0.023" : "0.018").add(surcharge ? new BigDecimal("0.006") : new BigDecimal("-0.0025").multiply(BigDecimal.valueOf(discounts)));
        BigDecimal pvEmployer = new BigDecimal(t.saxony() ? "0.013" : "0.018");
        for (Map<String, Double> side : List.of(employee, employer)) {
            contributions(side, "", pensionRegular, healthRegular, kvRate, side == employee ? pvEmployee : pvEmployer);
            if (oneOff.signum() > 0) contributions(side, " · thirteenth", pensionExtra, healthExtra, kvRate, side == employee ? pvEmployee : pvEmployer);
        }
        put(employer, "U1", pensionRegular.multiply(t.u1Rate())); put(employer, "U2", pensionRegular.multiply(t.u2Rate()));
        put(employer, "Insolvency levy", pensionRegular.multiply(t.insolvencyRate()));
        if (oneOff.signum() > 0) put(employer, "Insolvency levy · thirteenth", pensionExtra.multiply(t.insolvencyRate()));
        return new TaxCalculationService.Result(employee, employer, "BMF PAP 2026 v1; Teilmonat/sonstiger Bezug; SV 2026")
                .withGermanBasis(new Basis(svDays, pensionRegular.add(pensionExtra), healthRegular.add(healthExtra), annual));
    }
    private static void contributions(Map<String, Double> target, String suffix, BigDecimal rv, BigDecimal kv, BigDecimal kvRate, BigDecimal pvRate) {
        put(target, "Pension insurance" + suffix, rv.multiply(new BigDecimal("0.093")));
        put(target, "Unemployment insurance" + suffix, rv.multiply(new BigDecimal("0.013")));
        put(target, "Health insurance" + suffix, kv.multiply(kvRate)); put(target, "Nursing insurance" + suffix, kv.multiply(pvRate));
    }
    public static TaxCalculationService.Result calculate(PayrollTerms t, BigDecimal gross, LocalDate start, LocalDate end) {
        if (start.getYear() != 2026) throw new IllegalStateException("Deutsche Jahresparameter sind nur für 2026 installiert.");
        if (gross.compareTo(new BigDecimal("2000")) <= 0) throw new IllegalStateException("Mini-/Midijobs und Monate mit Entgeltausfall benötigen eigene Sozialversicherungsregeln; keine pauschale Halbierung.");
        if (!t.birthDate().plusYears(64).isAfter(end)) throw new IllegalStateException("Altersentlastung und Rentenstatus müssen vor der deutschen Abrechnung gesondert eingerichtet werden.");
        LocalDate surchargeStart = t.birthDate().plusYears(23).withDayOfMonth(1).plusMonths(1);
        boolean surcharge = !t.parent() && !start.isBefore(surchargeStart);
        int discounts = t.parent() ? Math.min(4, Math.max(0, t.childrenUnder25() - 1)) : 0;
        BigDecimal pvEmployee = new BigDecimal(t.saxony() ? "0.023" : "0.018")
                .add(surcharge ? new BigDecimal("0.006") : new BigDecimal("-0.0025").multiply(BigDecimal.valueOf(discounts)));
        BigDecimal pvEmployer = new BigDecimal(t.saxony() ? "0.013" : "0.018");
        var pap = new BmfLohnsteuer2026();
        pap.LZZ = 2; pap.RE4 = gross.movePointRight(2); pap.STKL = t.taxClass();
        pap.af = t.taxFactor().compareTo(BigDecimal.ONE) == 0 ? 0 : 1; pap.f = t.taxFactor().doubleValue();
        pap.ZKF = t.childAllowance(); pap.R = t.churchRate().signum() > 0 ? 1 : 0;
        pap.KVZ = t.healthAdditionalRate().movePointRight(2);
        pap.PVS = t.saxony() ? 1 : 0; pap.PVZ = surcharge ? 1 : 0; pap.PVA = BigDecimal.valueOf(discounts);
        pap.LZZFREIB = t.taxAllowance().movePointRight(2); pap.LZZHINZU = t.taxAddition().movePointRight(2);
        pap.calculate();
        Map<String, Double> employee = new LinkedHashMap<>(), employer = new LinkedHashMap<>();
        put(employee, "Income tax", pap.LSTLZZ.movePointLeft(2));
        put(employee, "Solidarity surcharge", pap.SOLZLZZ.movePointLeft(2));
        put(employee, "Church tax", pap.BK.movePointLeft(2).multiply(t.churchRate()));
        BigDecimal rvBase = gross.min(new BigDecimal("8450")), kvBase = gross.min(new BigDecimal("5812.50"));
        BigDecimal kvRate = new BigDecimal("0.073").add(t.healthAdditionalRate().divide(BigDecimal.valueOf(2)));
        for (Map<String, Double> side : List.of(employee, employer)) {
            put(side, "Pension insurance", rvBase.multiply(new BigDecimal("0.093")));
            put(side, "Unemployment insurance", rvBase.multiply(new BigDecimal("0.013")));
            put(side, "Health insurance", kvBase.multiply(kvRate));
        }
        put(employee, "Nursing insurance", kvBase.multiply(pvEmployee));
        put(employer, "Nursing insurance", kvBase.multiply(pvEmployer));
        put(employer, "U1", rvBase.multiply(t.u1Rate())); put(employer, "U2", rvBase.multiply(t.u2Rate()));
        put(employer, "Insolvency levy", rvBase.multiply(t.insolvencyRate()));
        return new TaxCalculationService.Result(employee, employer, "BMF PAP 2026 v1 / SHA256 63d8981646d139eba2f4dd990c13b43c4fb3883b402a5a40cddf253aa7aa96b4; SV 2026");
    }
    private static void put(Map<String, Double> map, String name, BigDecimal amount) { map.put(name, amount.setScale(2, RoundingMode.HALF_UP).doubleValue()); }
}
