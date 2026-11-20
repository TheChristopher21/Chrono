package com.chrono.chrono.services.payroll;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
/** 2025 PAP exclusively for actual 2025 tax assignments; no 2025 cash payroll. */
public final class GermanHistoricalTax {
 private GermanHistoricalTax() {}
    public static Map<String,Double> taxes(PayrollTerms t,PayrollGrossCalculator.Result gross,LocalDate start,LocalDate end,
            com.chrono.chrono.services.PayrollLedgerService.History history,BigDecimal oneOff,BigDecimal annualOverride) {
        if(start.getYear()!=2025)throw new IllegalStateException("Nur BMF-Jahresregeln 2025 sind installiert.");
        boolean full=PayrollPeriod.fullMonth(start,end);int calendarDays=(int)java.time.temporal.ChronoUnit.DAYS.between(start,end)+1;
        BigDecimal regular=gross.total().subtract(oneOff),annual=null;
        if(oneOff.signum()>0) {
            history.requireComplete();if(t.contract()==null||t.contract().germanPreviousEmploymentGross()==null)throw new IllegalStateException("Für sonstige Bezüge fehlt das ausdrücklich belegte Vorarbeitgeber-Jahresbrutto (auch 0 ausdrücklich erfassen).");
            annual=regular.add(t.contract().germanPreviousEmploymentGross());for(var e:history.entries())annual=annual.add(e.calculation().gross().total());
            BigDecimal monthly=t.hourly()?regular.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(full?30:Math.min(30,calendarDays)),12,RoundingMode.HALF_UP):t.amount();
            LocalDate until=t.exitDate()!=null&&t.exitDate().isBefore(end.withMonth(12).withDayOfMonth(31))?t.exitDate():end.withMonth(12).withDayOfMonth(31);
            for(LocalDate cursor=end.plusDays(1);!cursor.isAfter(until);) {var last=cursor.withDayOfMonth(cursor.lengthOfMonth());var through=last.isAfter(until)?until:last;annual=annual.add(PayrollPeriod.prorate(monthly,cursor,through,t.contract().salaryProration()));cursor=last.plusDays(1);}
        }
        if(annualOverride!=null)annual=annualOverride;
        var pap=new BmfLohnsteuer2025();pap.LZZ=full?2:4;pap.RE4=full?regular.movePointRight(2):regular.movePointRight(2).divide(BigDecimal.valueOf(calendarDays),0,RoundingMode.DOWN);
        pap.STKL=t.taxClass();pap.af=t.taxFactor().compareTo(BigDecimal.ONE)==0?0:1;pap.f=t.taxFactor().doubleValue();pap.ZKF=t.childAllowance();pap.R=t.churchRate().signum()>0?1:0;
        pap.KVZ=t.healthAdditionalRate().movePointRight(2);pap.PVS=t.saxony()?1:0;pap.PVZ=!t.parent()&&!start.isBefore(t.birthDate().plusYears(23).withDayOfMonth(1).plusMonths(1))?1:0;
        pap.PVA=BigDecimal.valueOf(t.parent()?Math.min(4,Math.max(0,t.childrenUnder25()-1)):0);
        pap.LZZFREIB=full?t.taxAllowance().movePointRight(2):t.taxAllowance().movePointRight(2).divide(BigDecimal.valueOf(30),0,RoundingMode.DOWN);
        pap.LZZHINZU=full?t.taxAddition().movePointRight(2):t.taxAddition().movePointRight(2).divide(BigDecimal.valueOf(30),0,RoundingMode.DOWN);
        if(oneOff.signum()>0){pap.SONSTB=oneOff.movePointRight(2);pap.JRE4=annual.movePointRight(2);pap.JFREIB=t.taxAllowance().multiply(BigDecimal.valueOf(1200));pap.JHINZU=t.taxAddition().multiply(BigDecimal.valueOf(1200));}
        pap.calculate();var multiplier=BigDecimal.valueOf(full?1:calendarDays);Map<String,Double> result=new LinkedHashMap<>();
        put(result,"Income tax",pap.LSTLZZ.multiply(multiplier).movePointLeft(2));put(result,"Solidarity surcharge",pap.SOLZLZZ.multiply(multiplier).movePointLeft(2));put(result,"Church tax",pap.BK.multiply(multiplier).movePointLeft(2).multiply(t.churchRate()));
        if(oneOff.signum()>0){put(result,"Income tax · thirteenth",pap.STS.movePointLeft(2));put(result,"Solidarity surcharge · thirteenth",pap.SOLZS.movePointLeft(2));put(result,"Church tax · thirteenth",pap.BKS.movePointLeft(2).multiply(t.churchRate()));}
        return result;
    }
    private static void put(Map<String,Double> m,String k,BigDecimal v){if(v.signum()!=0)m.put(k,v.setScale(2,RoundingMode.HALF_UP).doubleValue());}
}
