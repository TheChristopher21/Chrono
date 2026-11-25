package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** KS45: independent contiguous canton/status periods, with explicit attribution of later corrections. */
public final class SwissWithholdingSettlement {
    private SwissWithholdingSettlement() {}
    public record Allocation(String key,String canton,LocalDate from,BigDecimal amount,BigDecimal rateIncome,BigDecimal taxableBase) {}
    public record Result(BigDecimal amount,BigDecimal annualRate,BigDecimal monthlyRate,List<Allocation> allocations) {}
    private record Row(PayrollTerms terms,LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,PayrollPeriodData data) {}
    private record Segment(String key,List<Row> rows) {
        Row first(){return rows.get(0);} Row last(){return rows.get(rows.size()-1);}
        boolean source(){return "CH_SOURCE".equals(first().terms().taxMode());}
        boolean annual(){return source()&&SwissSourceTax.annualModel(first().terms().canton());}
    }
    public static Result calculate(SwissSourceTax tax,PayrollTerms t,LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,
            PayrollLedgerService.History history,PayrollPeriodData data) {
        List<Row> rows=new ArrayList<>();
        for(var e:history.entries()) {
            var c=e.calculation(); rows.add(new Row(c.terms(),c.periodStart(),c.periodEnd(),c.gross(),c.periodInputs()==null?null:c.periodInputs().data()));
        }
        rows.add(new Row(t,start,end,gross,data));
        List<Segment> segments=new ArrayList<>();
        for(Row row:rows) {
            if(segments.isEmpty() || !sameJurisdiction(segments.get(segments.size()-1).last(),row)) {
                String key=row.terms().taxMode()+":"+Objects.toString(row.terms().canton(),"")+":"+row.start();
                segments.add(new Segment(key,new ArrayList<>()));
            }
            segments.get(segments.size()-1).rows().add(row);
        }
        if(segments.stream().anyMatch(Segment::annual)) history.requireComplete();
        Map<String,BigDecimal> alreadyPaid=new HashMap<>();
        for(var e:history.entries()) {
            var c=e.calculation();
            var allocations=c.swissBasis()==null?null:c.swissBasis().withholdingAllocations();
            if(allocations!=null) for(var a:allocations) alreadyPaid.merge(a.key(),a.amount(),BigDecimal::add);
            else if("CH_SOURCE".equals(c.terms().taxMode())) {
                var segment=segments.stream().filter(s -> !c.periodStart().isBefore(s.first().start())&&!c.periodStart().isAfter(s.last().end())).findFirst().orElseThrow();
                alreadyPaid.merge(segment.key(),BigDecimal.valueOf(c.employee().getOrDefault("Withholding tax",0.0)),BigDecimal::add);
            }
        }
        List<Allocation> allocations=new ArrayList<>(); BigDecimal due=BigDecimal.ZERO,annual=null,monthly=null;
        for(int i=0;i<segments.size();i++) {
            Segment segment=segments.get(i); if(!segment.source()) continue;
            boolean active=i==segments.size()-1;
            if(segment.annual()) {
                BigDecimal rate=annualRate(segment,active,rows);
                Map<String,BigDecimal> byTariff=new LinkedHashMap<>();
                for(var row:segment.rows()) byTariff.merge(row.terms().tariffCode(),row.gross().total(),BigDecimal::add);
                BigDecimal target=BigDecimal.ZERO,taxable=BigDecimal.ZERO;
                for(var band:byTariff.entrySet()) {
                    target=target.add(tax.annual(start.getYear(),segment.first().terms().canton(),band.getKey(),band.getValue(),rate).amount());
                    taxable=taxable.add(band.getValue());
                }
                BigDecimal adjustment=target.subtract(alreadyPaid.getOrDefault(segment.key(),BigDecimal.ZERO));
                allocations.add(new Allocation(segment.key(),segment.first().terms().canton(),segment.first().start(),adjustment,rate,taxable));
                due=due.add(adjustment); if(active)annual=rate;
            } else if(active) {
                BigDecimal rate=monthlyRate(t,start,end,gross,history,data);
                BigDecimal amount=tax.monthly(start.getYear(),t.canton(),t.tariffCode(),gross.total(),rate).amount();
                allocations.add(new Allocation(segment.key(),t.canton(),segment.first().start(),amount,rate,gross.total()));
                due=due.add(amount); monthly=rate;
            }
        }
        return new Result(due,annual,monthly,List.copyOf(allocations));
    }
    private static boolean sameJurisdiction(Row first,Row next) {
        return first.terms().taxMode().equals(next.terms().taxMode())
                && (!"CH_SOURCE".equals(first.terms().taxMode()) || Objects.equals(first.terms().canton(),next.terms().canton()));
    }
    private static BigDecimal monthlyRate(PayrollTerms t,LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data) {
        int days=PayrollPeriod.swissDays(start,end);
        PayrollRatio periodic=PayrollRatio.of(gross.periodicBase()).multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(days));
        if(gross.thirteenthAmount().signum()>0) {
            int eligible=days;
            for(int i=history.entries().size()-1;i>=0;i--) {
                var c=history.entries().get(i).calculation(); if(c.gross().thirteenthAmount().signum()>0)break;
                if(c.terms().thirteenthMonths()>0)eligible+=PayrollPeriod.swissDays(c.periodStart(),c.periodEnd());
            }
            periodic=periodic.add(PayrollRatio.of(gross.thirteenthAmount()).multiply(BigDecimal.valueOf(t.thirteenthMonths()*30L)).divide(BigDecimal.valueOf(eligible)));
        }
        if(t.multipleEmployment()!=null) {
            if("KNOWN_OTHER_INCOME".equals(t.multipleEmployment().method())) periodic=periodic.add(PayrollRatio.of(t.multipleEmployment().otherPeriodicMonthlyGross()));
            else periodic=scaleOwn(periodic,t);
        }
        return periodic.add(PayrollRatio.of(gross.premium().add(gross.payout()).add(otherAperiodic(t,data)))).rateIncome();
    }
    private static BigDecimal otherAperiodic(PayrollTerms terms,PayrollPeriodData data) {
        if(terms.multipleEmployment()==null || !"KNOWN_OTHER_INCOME".equals(terms.multipleEmployment().method())) return BigDecimal.ZERO;
        if(data==null || data.swissOtherAperiodicGross()==null) throw new IllegalStateException("Weitere aperiodische Einkünfte der Periode ausdrücklich als Monatsgrundlage erfassen, gegebenenfalls mit 0.");
        return data.swissOtherAperiodicGross();
    }
    public static BigDecimal annualIncome(PayrollTerms t,LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,PayrollLedgerService.History history) {
        List<Row> rows=new ArrayList<>();
        for(var e:history.entries()){var c=e.calculation();rows.add(new Row(c.terms(),c.periodStart(),c.periodEnd(),c.gross(),c.periodInputs()==null?null:c.periodInputs().data()));}
        rows.add(new Row(t,start,end,gross,null));return annualRate(new Segment("single-period",rows),true,rows);
    }
    private static BigDecimal annualRate(Segment segment,boolean active,List<Row> yearRows) {
        Row last=segment.last(); PayrollTerms t=last.terms();
        PayrollRatio periodic=PayrollRatio.ZERO,eligible=PayrollRatio.ZERO,aperiodic=PayrollRatio.ZERO;
        BigDecimal ownPeriodic=BigDecimal.ZERO,ownEligible=BigDecimal.ZERO,ownEligibleForForecast=BigDecimal.ZERO;
        int elapsed=0;
        for(Row r:segment.rows()) {
            int days=PayrollPeriod.swissDays(r.start(),r.end()); elapsed+=days;
            ownPeriodic=ownPeriodic.add(r.gross().periodicBase());
            ownEligibleForForecast=ownEligibleForForecast.add(r.gross().eligibleThirteenthBase());
            periodic=periodic.add(scaleOwn(PayrollRatio.of(r.gross().periodicBase()),r.terms())).add(otherPeriodicRatio(r.terms(),days));
            if(r.terms().thirteenthMonths()>0) {
                ownEligible=ownEligible.add(r.gross().eligibleThirteenthBase());
                eligible=eligible.add(scaleOwn(PayrollRatio.of(r.gross().eligibleThirteenthBase()),r.terms()));
            }
            aperiodic=aperiodic.add(PayrollRatio.of(r.gross().payout().add(r.gross().premium()).add(otherAperiodic(r.terms(),r.data()))));
        }
        LocalDate until=active?(t.exitDate()!=null&&t.exitDate().getYear()==last.end().getYear()?t.exitDate():last.end().withMonth(12).withDayOfMonth(31)):last.end();
        int futureDays=PayrollPeriod.swissDays(last.end().plusDays(1),until);
        PayrollRatio forecast=t.hourly()?PayrollRatio.of(ownPeriodic).multiply(BigDecimal.valueOf(futureDays)).divide(BigDecimal.valueOf(elapsed))
                :PayrollRatio.of(SwissPayrollSettlement.futureSalary(t,last.end().plusDays(1),until));
        PayrollRatio forecastEligible=t.hourly()?PayrollRatio.of(ownEligibleForForecast).multiply(BigDecimal.valueOf(futureDays)).divide(BigDecimal.valueOf(elapsed)):forecast;
        periodic=periodic.add(scaleOwn(forecast,t)).add(otherPeriodicRatio(t,futureDays));
        PayrollRatio thirteenth=eligible.add(t.thirteenthMonths()>0?scaleOwn(forecastEligible,t):PayrollRatio.ZERO).divide(BigDecimal.valueOf(12));
        if(active && futureDays==0) {
            // The final phase receives the actual rounding residue; closed canton periods are not reopened
            // by a later thirteenth payment (KS45 8.2–8.4).
            BigDecimal actual=yearRows.stream().map(r->r.gross().thirteenthAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            PayrollRatio previousAccrual=PayrollRatio.of(yearRows.stream().filter(r->r.end().isBefore(segment.first().start())&&r.terms().thirteenthMonths()>0)
                    .map(r->r.gross().eligibleThirteenthBase()).reduce(BigDecimal.ZERO,BigDecimal::add)).divide(BigDecimal.valueOf(12));
            PayrollRatio ownFinal=PayrollRatio.of(actual).subtract(previousAccrual).nonnegative();
            // For changing employment percentages, use the weighted conversion of earned components.
            thirteenth=thirteenth.add(scaleOwn(ownFinal.subtract(PayrollRatio.of(ownEligible).divide(BigDecimal.valueOf(12))),t));
        }
        return periodic.add(thirteenth).multiply(BigDecimal.valueOf(360)).divide(BigDecimal.valueOf(elapsed+futureDays)).add(aperiodic).rateIncome();
    }
    private static PayrollRatio scaleOwn(PayrollRatio own,PayrollTerms t) {
        var m=t.multipleEmployment();if(m==null||"KNOWN_OTHER_INCOME".equals(m.method()))return own;
        return own.multiply("TOTAL_PERCENT".equals(m.method())?m.totalPercent():BigDecimal.valueOf(100)).divide(m.ownPercent());
    }
    private static PayrollRatio otherPeriodicRatio(PayrollTerms t,int days) {
        var m=t.multipleEmployment();return m!=null&&"KNOWN_OTHER_INCOME".equals(m.method())?PayrollRatio.of(m.otherPeriodicMonthlyGross()).multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(30)):PayrollRatio.ZERO;
    }
}
