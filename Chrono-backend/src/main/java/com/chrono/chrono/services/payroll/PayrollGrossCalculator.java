package com.chrono.chrono.services.payroll;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Minutes are integral; monetary components are rounded once, at the end of each component. */
public final class PayrollGrossCalculator {
    private PayrollGrossCalculator() {}
    public record Result(BigDecimal base, BigDecimal premium, BigDecimal payout, BigDecimal total, int paidOvertimeMinutes, BigDecimal thirteenth) {
        public Result(BigDecimal base, BigDecimal premium, BigDecimal payout, BigDecimal total, int minutes) { this(base, premium, payout, total, minutes, BigDecimal.ZERO); }
        public BigDecimal thirteenthAmount() { return thirteenth == null ? BigDecimal.ZERO : thirteenth; }
        public Result withThirteenth(BigDecimal amount) { return new Result(base, premium, payout, base.add(premium).add(payout).add(amount), paidOvertimeMinutes, amount); }
    }
    public static Result calculate(PayrollTerms terms, LocalDate start, LocalDate end,
            List<DailyTimeSummaryDTO> days, Double requestedHours, boolean payout, Integer balanceMinutes) {
        Map<LocalDate, Integer> weekMinutes = new HashMap<>();
        int worked = 0, overtime = 0;
        Set<LocalDate> seen = new HashSet<>();
        for (DailyTimeSummaryDTO day : days.stream().sorted(Comparator.comparing(DailyTimeSummaryDTO::getDate)).toList()) {
            if (!seen.add(day.getDate()) || day.getWorkedMinutes() < 0) throw new IllegalStateException("Doppelte oder ungültige Tageswerte.");
            if (day.isNeedsCorrection() || day.getPrimaryTimes() != null && day.getPrimaryTimes().isOpen()) throw new IllegalStateException("Offene oder korrekturbedürftige Stempel müssen zuerst geklärt werden.");
            if (!day.getDate().isBefore(start) && !day.getDate().isAfter(end)
                    && day.getExpectedMinutes() != null && day.getExpectedMinutes() > 0 && !Boolean.TRUE.equals(day.getHasTrackedEntries())) throw new IllegalStateException("Sollzeit ohne Zeiterfassung oder Abwesenheit am " + day.getDate() + ".");
            if (day.getDate().isAfter(end)) continue;
            LocalDate monday = day.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int previous = weekMinutes.getOrDefault(monday, 0), next = Math.addExact(previous, day.getWorkedMinutes());
            weekMinutes.put(monday, next);
            if (day.getDate().isBefore(start)) continue;
            worked = Math.addExact(worked, day.getWorkedMinutes());
            int contract = terms.weeklyHours().multiply(BigDecimal.valueOf(60)).intValueExact();
            overtime += Math.max(0, next - contract) - Math.max(0, previous - contract);
            if (terms.hourly() && "CH".equals(terms.country()) && next > terms.weeklyLimit() * 60
                    && terms.overtimePremiumRate().compareTo(new BigDecimal("0.25")) < 0) throw new IllegalStateException("Gesetzliche Überzeit: Ein Zuschlag unter 25 % ist ohne gesonderte Prüfung nicht abrechenbar.");
        }
        BigDecimal base = terms.hourly() ? minutesPay(worked, terms.amount(), terms.country()) : PayrollPeriod.prorate(terms.amount(), start, end, terms.contract() == null ? null : terms.contract().salaryProration());
        BigDecimal premium = terms.hourly() ? minutesPay(overtime, terms.amount().multiply(terms.overtimePremiumRate()), terms.country()) : money(BigDecimal.ZERO);
        int paidMinutes = 0;
        BigDecimal additional = money(BigDecimal.ZERO);
        if (payout) {
            if (terms.hourly()) throw new IllegalStateException("Stundenlohn vergütet bereits sämtliche gearbeiteten Stunden. Eine zusätzliche Auszahlung aus dem Stundensaldo würde Stunden doppelt bezahlen.");
            if (requestedHours == null || !Double.isFinite(requestedHours) || requestedHours <= 0) throw new IllegalArgumentException("Positive Überstundenanzahl erforderlich.");
            paidMinutes = payoutMinutes(requestedHours);
            if (balanceMinutes == null || paidMinutes > Math.max(0, balanceMinutes)) throw new IllegalStateException("Auszahlung übersteigt den verfügbaren Überstundensaldo.");
            if ("CH".equals(terms.country()) && terms.overtimePremiumRate().compareTo(new BigDecimal("0.25")) < 0) throw new IllegalStateException("Der historische Saldo trennt Überstunden und Überzeit noch nicht. Eine Auszahlung mit weniger als 25 % Zuschlag benötigt eine gesonderte Zuordnung.");
            additional = minutesPay(paidMinutes, terms.overtimeHourlyRate().multiply(BigDecimal.ONE.add(terms.overtimePremiumRate())), terms.country());
        }
        return new Result(base, premium, additional, base.add(premium).add(additional), paidMinutes);
    }
    public static int payoutMinutes(double hours) {
        if (!Double.isFinite(hours) || hours <= 0) throw new IllegalArgumentException("Positive Überstundenanzahl erforderlich.");
        BigDecimal minutes = BigDecimal.valueOf(hours).multiply(BigDecimal.valueOf(60));
        BigDecimal rounded = minutes.setScale(0, RoundingMode.HALF_UP);
        // Hours are a legacy JSON double. Accept only representation residue, not fractions of a minute.
        if (minutes.subtract(rounded).abs().compareTo(new BigDecimal("0.000000001")) > 0) throw new IllegalArgumentException("Überstunden müssen auf ganze Minuten genau angegeben werden.");
        try { int result = rounded.intValueExact(); if (result <= 0) throw new ArithmeticException(); return result; }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Ungültige Anzahl Überstunden."); }
    }
    private static BigDecimal minutesPay(int minutes, BigDecimal rate, String country) {
        BigDecimal step = new BigDecimal("CH".equals(country) ? "0.05" : "0.01");
        return rate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60).multiply(step), 0, RoundingMode.HALF_UP).multiply(step);
    }
    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
