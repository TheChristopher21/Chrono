package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.*;
import com.chrono.chrono.entities.PayrollConfigurationVersion;
import java.math.*;
import java.time.*;
import java.util.*;

/** Swissdec 7.12/7.13: separate insurer/code ceilings; AHV/ALV are independent domains. */
public final class SwissInsuranceSettlement {
    private SwissInsuranceSettlement() {}
    public record Slice(LocalDate from,LocalDate through,int days,Long insuranceVersion,Long pensionVersion,
            PayrollInsurancePool uvgPool,PayrollKtgPool ktgPool,BigDecimal ktgAnnualCap,BigDecimal gross,
            BigDecimal uvgBase,BigDecimal ktgBase,BigDecimal pensionEmployee,BigDecimal pensionEmployer) {}
    public static TaxCalculationService.Result calculate(SwissSourceTax tax,PayrollConfigurationService.Coverage coverage,PayrollTerms t,
            LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data) {
        return calculate(tax,coverage,t,start,end,gross,history,data,true);
    }
    public static TaxCalculationService.Result calculate(SwissSourceTax tax,PayrollConfigurationService.Coverage coverage,PayrollTerms t,
            LocalDate start,LocalDate end,PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data,boolean includeTax) {
        history.requireComplete();
        List<PayrollConfigurationService.CoverageSlice> policies=coverage.slices()==null||coverage.slices().isEmpty()
                ?List.of(new PayrollConfigurationService.CoverageSlice(start,end,coverage.insurance(),coverage.pension())):coverage.slices();
        List<PayrollPeriodData.SwissInsuranceAllocation> inputs=data==null?null:data.swissInsuranceAllocations();
        if(policies.size()>1&&(inputs==null||inputs.size()!=policies.size()))throw new IllegalStateException("Bei untermonatigem Versicherungs-/BVG-Wechsel die tatsächlichen Bruttolöhne und vereinbarten BVG-Beiträge je Deckungsabschnitt als Monatsgrundlage erfassen.");
        if(data!=null)PayrollPeriodInputService.validate(data,t,start,end);
        if(inputs!=null&&inputs.size()!=policies.size())throw new IllegalStateException("Lohnaufteilung und datierte Versicherungsversionen haben unterschiedliche Abschnitte.");
        List<Slice> earlier=new ArrayList<>();BigDecimal yearGross=gross.total(),previousAlv=BigDecimal.ZERO;int yearDays=PayrollPeriod.swissDays(start,end);
        for(var entry:history.entries()) {
            var c=entry.calculation();var basis=c.swissBasis();if(basis==null)throw new IllegalStateException("Versicherungsgrundlagen einer Vorperiode fehlen.");
            yearGross=yearGross.add(c.gross().total());previousAlv=previousAlv.add(basis.alvUvgBase());yearDays+=basis.insuranceDays();
            if(basis.insuranceSlices()!=null&&!basis.insuranceSlices().isEmpty())earlier.addAll(basis.insuranceSlices());
            else {
                BigDecimal cap=basis.ktgAnnualSalaryCap();
                if(cap==null&&c.taxReference()!=null&&c.taxReference().startsWith("Versicherungsversion #"+coverage.insurance().getId()+";"))cap=coverage.insurance().getKtgAnnualSalaryCap();
                if(cap==null)throw new IllegalStateException("Die gesicherte KTG-Grenze einer Vorperiode fehlt.");
                earlier.add(new Slice(c.periodStart(),c.periodEnd(),basis.insuranceDays(),null,null,null,basis.ktgPool(),cap,c.gross().total(),basis.alvUvgBase(),basis.ktgBase(),null,null));
            }
        }
        BigDecimal alv=yearGross.min(cap(new BigDecimal("148200"),yearDays)).subtract(previousAlv);
        if(alv.signum()<0)throw new IllegalStateException("Bisherige ALV-Basen zuerst chronologisch berichtigen.");
        Map<String,BigDecimal> employee=new LinkedHashMap<>(),employer=new LinkedHashMap<>();
        boolean liable=start.getYear()>t.birthDate().getYear()+17;
        if(liable&&gross.total().signum()>0&&gross.periodicBase().multiply(BigDecimal.valueOf(360)).divide(BigDecimal.valueOf(PayrollPeriod.swissDays(start,end)),12,RoundingMode.HALF_UP).compareTo(new BigDecimal("2500"))<=0)throw new IllegalStateException("Geringfügiger Jahreslohn: Beitragspflicht ausdrücklich klären.");
        BigDecimal ahv=liable?gross.total().multiply(new BigDecimal("0.053")):BigDecimal.ZERO;
        add(employee,"AHV/IV/EO",ahv);add(employer,"AHV/IV/EO",ahv);add(employee,"ALV",liable?alv.multiply(new BigDecimal("0.011")):BigDecimal.ZERO);add(employer,"ALV",liable?alv.multiply(new BigDecimal("0.011")):BigDecimal.ZERO);
        List<Slice> current=new ArrayList<>();BigDecimal allocated=BigDecimal.ZERO,ktgTotal=BigDecimal.ZERO;
        StringBuilder reference=new StringBuilder("CH "+start.getYear()+"; datierte Versicherungsabschnitte");
        for(int index=0;index<policies.size();index++) {
            var policy=policies.get(index);var i=policy.insurance();var p=policy.pension();
            if(!new PayrollConfigurationService.Coverage(i,p,false,false).insuranceComplete()||!new PayrollConfigurationService.Coverage(i,p,false,false).pensionComplete())throw new IllegalStateException("Ein Versicherungsabschnitt hat unvollständige Vertragswerte.");
            var input=inputs==null?null:inputs.get(index);
            if(input!=null&&(!input.from().equals(policy.from())||!input.through().equals(policy.through())))throw new IllegalStateException("Die Bruttolohnabschnitte müssen exakt den datierten Versicherungs-/BVG-Wechseln entsprechen.");
            BigDecimal wage=input==null?gross.total():input.gross();allocated=allocated.add(wage);
            BigDecimal pensionEmployee=input==null?PayrollPeriod.prorate(p.getBvgEmployee(),start,end,t.contract()==null?null:t.contract().pensionProration()):input.pensionEmployee();
            BigDecimal pensionEmployer=input==null?PayrollPeriod.prorate(p.getBvgEmployer(),start,end,t.contract()==null?null:t.contract().pensionProration()):input.pensionEmployer();
            var uvg=i.getUvgPoolKey()==null?null:new PayrollInsurancePool(i.getUvgPoolKey(),i.getUvgPoolFrom(),i.getUvgPoolThrough());
            var ktg=i.getKtgPoolKey()==null?null:new PayrollKtgPool(i.getKtgPoolKey(),i.getKtgPoolFrom(),i.getKtgPoolThrough());
            checkPeriod(uvg==null?null:uvg.from(),uvg==null?null:uvg.through(),policy);checkPeriod(ktg==null?null:ktg.from(),ktg==null?null:ktg.through(),policy);
            int days=PayrollPeriod.swissDays(policy.from(),policy.through());
            BigDecimal uvgBase=poolBase(earlier,current,uvg,ktg,false,new BigDecimal("148200"),wage,policy.from(),policy.through());
            BigDecimal ktgBase=poolBase(earlier,current,uvg,ktg,true,i.getKtgAnnualSalaryCap(),wage,policy.from(),policy.through());ktgTotal=ktgTotal.add(ktgBase);
            current.add(new Slice(policy.from(),policy.through(),days,i.getId(),p.getId(),uvg,ktg,i.getKtgAnnualSalaryCap(),wage,uvgBase,ktgBase,pensionEmployee,pensionEmployer));
            if(t.weeklyHours().compareTo(new BigDecimal("8"))>=0)add(employee,"NBU",uvgBase.multiply(i.getUvgNbuRate()));
            add(employer,"BU",uvgBase.multiply(i.getUvgBuRate()));add(employee,"KTG",ktgBase.multiply(i.getKtgRateEmployee()));add(employer,"KTG",ktgBase.multiply(i.getKtgRateEmployer()));
            add(employee,"FAK",liable?wage.multiply(i.getFakEmployeeRate()):BigDecimal.ZERO);add(employer,"FAK",liable?wage.multiply(i.getFakRate()):BigDecimal.ZERO);
            add(employer,"AHV administration",liable?wage.multiply(new BigDecimal("0.106")).multiply(i.getAhvAdminRate()):BigDecimal.ZERO);
            add(employee,"BVG",pensionEmployee);add(employer,"BVG",pensionEmployer);
            reference.append("; ").append(policy.from()).append("–").append(policy.through()).append(" Versicherung #").append(i.getId()).append("/BVG #").append(p.getId());
        }
        if(allocated.compareTo(gross.total())!=0)throw new IllegalStateException("Die bestätigten Abschnittsbruttolöhne stimmen nicht mit dem gesamten Bruttolohn überein.");
        // An unchanged pension version still determines the complete month's agreed contribution.
        if(!coverage.pensionChangesWithinPeriod()&&inputs!=null) {
            var p=coverage.pension();String method=t.contract()==null?null:t.contract().pensionProration();
            if(current.stream().map(Slice::pensionEmployee).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(PayrollPeriod.prorate(p.getBvgEmployee(),start,end,method))!=0||current.stream().map(Slice::pensionEmployer).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(PayrollPeriod.prorate(p.getBvgEmployer(),start,end,method))!=0)throw new IllegalStateException("Die aufgeteilten BVG-Beiträge widersprechen dem unveränderten Monatsbeitrag.");
        }
        var withholding=includeTax?SwissWithholdingSettlement.calculate(tax,t,start,end,gross,history,data):new SwissWithholdingSettlement.Result(BigDecimal.ZERO,null,null,List.of());add(employee,"Withholding tax",withholding.amount());
        return new TaxCalculationService.Result(round(employee),round(employer),reference.toString(),new SwissPayrollSettlement.Basis(PayrollPeriod.swissDays(start,end),alv,ktgTotal,withholding.annualRate(),withholding.monthlyRate(),coverage.insurance().getKtgAnnualSalaryCap(),current.get(0).ktgPool(),withholding.allocations(),List.copyOf(current)));
    }
    private static void checkPeriod(LocalDate from,LocalDate through,PayrollConfigurationService.CoverageSlice period) {
        if(from!=null&&(through==null||period.from().isBefore(from)||period.through().isAfter(through)))throw new IllegalStateException("Versicherer-/Codekreis deckt den Abschnitt nicht vollständig ab.");
    }
    private static BigDecimal poolBase(List<Slice> past,List<Slice> current,PayrollInsurancePool uvg,PayrollKtgPool ktg,boolean isKtg,BigDecimal annual,BigDecimal gross,LocalDate start,LocalDate currentEnd) {
        BigDecimal total=gross,previous=BigDecimal.ZERO;LocalDate from=isKtg?(ktg==null?null:ktg.from()):(uvg==null?null:uvg.from());
        List<LocalDate[]> intervals=new ArrayList<>();intervals.add(new LocalDate[]{start,currentEnd});
        for(var row:java.util.stream.Stream.concat(past.stream(),current.stream()).toList()) {
            if(from!=null&&row.through().isBefore(from))continue;
            if(isKtg?!Objects.equals(ktg,row.ktgPool()):!Objects.equals(uvg,row.uvgPool()))throw new IllegalStateException("Versicherungs-/Codewechsel benötigt einen eigenen Kreis mit korrektem Deckungsbeginn; Höchstlöhne werden nicht übertragen.");
            if(isKtg&&annual.compareTo(row.ktgAnnualCap())!=0)throw new IllegalStateException("Widersprüchliche KTG-Lohngrenzen innerhalb desselben Kreises.");
            total=total.add(row.gross());previous=previous.add(isKtg?row.ktgBase():row.uvgBase());intervals.add(new LocalDate[]{row.from(),row.through()});
        }
        int totalDays=0;intervals.sort(Comparator.comparing(d->d[0]));LocalDate a=null,b=null;
        for(var interval:intervals) {
            if(a==null){a=interval[0];b=interval[1];}
            else if(!interval[0].isAfter(b.plusDays(1))) {if(interval[1].isAfter(b))b=interval[1];}
            else {totalDays+=PayrollPeriod.swissDays(a,b);a=interval[0];b=interval[1];}
        }
        totalDays+=PayrollPeriod.swissDays(a,b);
        BigDecimal target=annual.signum()==0?total:total.min(cap(annual,totalDays));var result=target.subtract(previous);
        if(result.signum()<0)throw new IllegalStateException("Vorperiodenbasis dieses Versicherers zuerst berichtigen.");return result;
    }
    private static BigDecimal cap(BigDecimal annual,int days){return annual.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360),12,RoundingMode.HALF_UP);}
    private static void add(Map<String,BigDecimal> map,String name,BigDecimal amount){map.merge(name,amount,BigDecimal::add);}
    private static Map<String,Double> round(Map<String,BigDecimal> map) {
        Map<String,Double> result=new LinkedHashMap<>();map.forEach((k,v)->{var value="BVG".equals(k)?v.setScale(2,RoundingMode.HALF_UP):v.divide(new BigDecimal("0.05"),0,RoundingMode.HALF_UP).multiply(new BigDecimal("0.05"));if(value.signum()!=0)result.put(k,value.doubleValue());});return result;
    }
}
