package com.chrono.chrono.services.payroll;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Minutes are integral; monetary components are rounded once, at the end of each component. */
public final class PayrollGrossCalculator {
    private PayrollGrossCalculator() {}
    public record Result(BigDecimal base, BigDecimal premium, BigDecimal payout, BigDecimal total, int paidOvertimeMinutes, BigDecimal thirteenth,
            Map<String,BigDecimal> additionalEarnings, BigDecimal thirteenthEligibleBase) {
        public Result(BigDecimal base, BigDecimal premium, BigDecimal payout, BigDecimal total, int minutes) { this(base, premium, payout, total, minutes, BigDecimal.ZERO,null,null); }
        public Result(BigDecimal base, BigDecimal premium, BigDecimal payout, BigDecimal total, int minutes,BigDecimal thirteenth) { this(base,premium,payout,total,minutes,thirteenth,null,null); }
        public BigDecimal thirteenthAmount() { return thirteenth == null ? BigDecimal.ZERO : thirteenth; }
        public Map<String,BigDecimal> extraEarnings() { return additionalEarnings==null?Map.of():additionalEarnings; }
        public BigDecimal eligibleThirteenthBase() { return thirteenthEligibleBase==null?base:thirteenthEligibleBase; }
        public BigDecimal periodicBase() { return base.add(extraEarnings().values().stream().reduce(BigDecimal.ZERO,BigDecimal::add)); }
        public Result withThirteenth(BigDecimal amount) { return new Result(base, premium, payout, periodicBase().add(premium).add(payout).add(amount), paidOvertimeMinutes, amount,additionalEarnings,thirteenthEligibleBase); }
    }
    public static Result calculate(PayrollTerms terms, LocalDate start, LocalDate end,
            List<DailyTimeSummaryDTO> days, Double requestedHours, boolean payout, Integer balanceMinutes) {
        return calculate(terms,start,end,days,requestedHours,payout,balanceMinutes,null);
    }
    public static Result calculate(PayrollTerms terms, LocalDate start, LocalDate end,
            List<DailyTimeSummaryDTO> days, Double requestedHours, boolean payout, Integer balanceMinutes,PayrollPeriodData periodData) {
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
            if (terms.hourly()) {
                int contract = terms.weeklyHours().multiply(BigDecimal.valueOf(60)).intValueExact();
                overtime = Math.addExact(overtime, Math.max(0, next - contract) - Math.max(0, previous - contract));
            }
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
        var result=new Result(base, premium, additional, base.add(premium).add(additional), paidMinutes);
        return compensateAbsencesAndAllowances(result,terms,start,end,days,periodData);
    }
    private static Result compensateAbsencesAndAllowances(Result result,PayrollTerms terms,LocalDate start,LocalDate end,
            List<DailyTimeSummaryDTO> days,PayrollPeriodData data) {
        var contract=terms.hourlyAllowance();
        if(data!=null) com.chrono.chrono.services.PayrollPeriodInputService.validate(data,terms,start,end);
        if(contract==null) return result;
        Map<String,BigDecimal> earnings=new LinkedHashMap<>(); BigDecimal paidAbsences=BigDecimal.ZERO;
        Map<LocalDate,Integer> minutes=new HashMap<>();
        days.stream().filter(d -> !d.getDate().isBefore(start)&&!d.getDate().isAfter(end)).forEach(d -> minutes.put(d.getDate(),d.getWorkedMinutes()));
        if(data!=null) for(var absence:data.absencePayments()) {
            if(minutes.merge(absence.date(),absence.minutes(),Math::addExact)>1440) throw new IllegalStateException("Arbeits- und Abwesenheitsminuten übersteigen einen Kalendertag.");
            String label=switch(absence.type()){case "VACATION"->"Paid vacation";case "HOLIDAY"->"Paid public holidays";default->"Employer sick pay";};
            // Sum unrounded amounts per component, then round once, like other earnings.
            BigDecimal value=absence.hourlyRate().multiply(absence.payRate()).multiply(BigDecimal.valueOf(absence.minutes()));
            earnings.merge(label,value,BigDecimal::add);
        }
        earnings.replaceAll((k,v)->{
            BigDecimal step=new BigDecimal("CH".equals(terms.country())?"0.05":"0.01");
            return v.divide(BigDecimal.valueOf(60).multiply(step),0,RoundingMode.HALF_UP).multiply(step);
        });
        paidAbsences=earnings.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal allowanceBase=result.base().add("BASE_AND_PREMIUM".equals(contract.allowanceBase())?result.premium():BigDecimal.ZERO);
        BigDecimal vacation=roundPay(allowanceBase.multiply(contract.vacationRate()),terms.country());
        BigDecimal holiday=roundPay(allowanceBase.multiply(contract.holidayRate()),terms.country());
        if(vacation.signum()!=0) earnings.put("Vacation allowance",vacation);
        if(holiday.signum()!=0) earnings.put("Holiday allowance",holiday);
        BigDecimal eligible=result.base();
        if(Boolean.TRUE.equals(contract.paidAbsencesInThirteenth())) eligible=eligible.add(paidAbsences);
        if(Boolean.TRUE.equals(contract.allowancesInThirteenth())) eligible=eligible.add(vacation).add(holiday);
        BigDecimal total=result.total().add(earnings.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        return new Result(result.base(),result.premium(),result.payout(),total,result.paidOvertimeMinutes(),result.thirteenthAmount(),Collections.unmodifiableMap(earnings),eligible);
    }
    private static BigDecimal roundPay(BigDecimal value,String country) {
        BigDecimal step=new BigDecimal("CH".equals(country)?"0.05":"0.01");
        return value.divide(step,0,RoundingMode.HALF_UP).multiply(step);
    }
    public static Map<String,Double> earnings(Result gross) {
        Map<String,Double> result=new LinkedHashMap<>(); result.put("Base salary",gross.base().doubleValue());
        if(gross.premium().signum()!=0) result.put("Overtime premium",gross.premium().doubleValue());
        if(gross.payout().signum()!=0) result.put("Overtime payout",gross.payout().doubleValue());
        if(gross.thirteenthAmount().signum()!=0) result.put("Thirteenth salary",gross.thirteenthAmount().doubleValue());
        gross.extraEarnings().forEach((k,v)->{if(v.signum()!=0)result.put(k,v.doubleValue());});
        return result;
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
