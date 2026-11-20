package com.chrono.chrono.services.payroll;
import com.chrono.chrono.services.*;
import com.chrono.chrono.entities.Payslip;
import java.math.*;
import java.time.*;
import java.util.*;

/** Assign one-offs chronologically by actual receipt month, including mixed March-clause years. */
public final class GermanSocialPayments {
    private GermanSocialPayments() {}
    private static final class Group {BigDecimal amount=BigDecimal.ZERO;PayrollPeriodData.GermanPriorYearSocial prior;PayrollPeriodData.GermanConcurrentIncome concurrent;Long version;}
    public static TaxCalculationService.Result calculate(PayrollTerms t,PayrollGrossCalculator.Result cash,PayrollRemuneration allocation,
            LocalDate start,LocalDate end,PayrollLedgerService.History history,PayrollPeriodData data) {
        BigDecimal originalOneOff=t.thirteenthMonths()==1?BigDecimal.ZERO:cash.thirteenthAmount();
        BigDecimal extra=allocation.applied().stream().filter(r->"SOCIAL".equals(r.domain())&&"ONE_OFF".equals(r.payment().kind())).map(r->r.payment().gross()).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal regular=allocation.socialGross().total().subtract(originalOneOff).subtract(extra);
        var regularGross=new PayrollGrossCalculator.Result(regular,BigDecimal.ZERO,BigDecimal.ZERO,regular,0);
        var result=GermanPayrollTax.calculateRun(t,regularGross,start,end,history,data,BigDecimal.ZERO);
        Map<String,Double> employee=new LinkedHashMap<>(result.getEmployee()),employer=new LinkedHashMap<>(result.getEmployer());
        employee.keySet().removeIf(k->k.startsWith("Income tax")||k.startsWith("Solidarity surcharge")||k.startsWith("Church tax"));
        SortedMap<YearMonth,Group> groups=new TreeMap<>();
        if(originalOneOff.signum()>0){var g=new Group();g.amount=originalOneOff;g.prior=data==null?null:data.germanPriorYearSocial();g.concurrent=data==null?null:data.germanConcurrentIncome();groups.put(YearMonth.from(end),g);}
        for(var ref:allocation.applied()) {
            if(!"SOCIAL".equals(ref.domain())||!"ONE_OFF".equals(ref.payment().kind()))continue;
            var g=groups.computeIfAbsent(YearMonth.from(ref.payment().receivedOn()),m->new Group());
            if(g.version!=null&&!g.version.equals(ref.versionId()))throw new IllegalStateException("Für Einmalzahlungen desselben Zuflussmonats die SV-Grundlagen gemeinsam und vollständig erfassen.");
            g.version=ref.versionId();g.amount=g.amount.add(ref.payment().gross());
            if(data!=null&&data.germanPriorYearSocial()!=null) {
                var derived=data.germanPriorYearSocial();var supplied=ref.priorSocial();
                g.prior=supplied==null?derived:new PayrollPeriodData.GermanPriorYearSocial(derived.assignmentDate(),derived.insuranceDays(),derived.pensionAssessed(),derived.healthAssessed(),derived.healthAdditionalRate(),derived.parent(),derived.childrenUnder25(),derived.saxony(),supplied.pensionAllocatedBase(),supplied.healthAllocatedBase(),derived.source()+"; "+supplied.source(),true);
            }else g.prior=ref.priorSocial()!=null?ref.priorSocial():g.prior;
            g.concurrent=ref.concurrent()!=null?ref.concurrent():g.concurrent;
        }
        BigDecimal currentPension=BigDecimal.ZERO,currentHealth=BigDecimal.ZERO,priorPension=BigDecimal.ZERO,priorHealth=BigDecimal.ZERO;LocalDate assignment=null;String priorSource=null;
        List<PayrollLedgerService.Entry> entries=new ArrayList<>(history.entries());
        for(var entry:groups.entrySet()) {
            var group=entry.getValue();var concurrent=data==null?null:data.germanConcurrentIncome();
            if(concurrent!=null&&group.concurrent!=null)concurrent=new PayrollPeriodData.GermanConcurrentIncome(concurrent.otherPensionGross(),concurrent.otherHealthGross(),group.concurrent.pensionOneOffBase(),group.concurrent.healthOneOffBase(),concurrent.fundAllocationConfirmed(),concurrent.employments());
            var periodData=new PayrollPeriodData(List.of(),concurrent,null,null,null,group.prior);
            var withOneOff=new PayrollGrossCalculator.Result(regular,BigDecimal.ZERO,BigDecimal.ZERO,regular.add(group.amount),0,group.amount);
            var h=new PayrollLedgerService.History(List.copyOf(entries),history.references(),history.complete());
            var calculated=GermanPayrollTax.calculateRun(t,withOneOff,start,end,h,periodData,group.amount,entry.getKey().atEndOfMonth());
            calculated.getEmployee().forEach((k,v)->{if(extraContribution(k))employee.merge(k.replace(" · thirteenth"," · one-off remuneration"),v,Double::sum);});
            calculated.getEmployer().forEach((k,v)->{if(extraContribution(k))employer.merge(k.replace(" · thirteenth"," · one-off remuneration"),v,Double::sum);});
            var b=calculated.getGermanBasis();BigDecimal rv=b.pensionBase().subtract(result.getGermanBasis().pensionBase()),kv=b.healthBase().subtract(result.getGermanBasis().healthBase());
            currentPension=currentPension.add(rv);currentHealth=currentHealth.add(kv);
            if(b.priorYear()!=null) {
                var p=b.priorYear();if(assignment!=null&&!assignment.equals(p.assignmentDate()))throw new IllegalStateException("Vorjahreszuordnungen müssen denselben letzten 2025-Abrechnungszeitraum bezeichnen.");assignment=p.assignmentDate();priorSource=p.source();priorPension=priorPension.add(p.pensionBase());priorHealth=priorHealth.add(p.healthBase());
            }
            var zero=new PayrollGrossCalculator.Result(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0);
            var deltaBasis=new GermanPayrollTax.Basis(0,rv,kv,null,b.priorYear());
            var c=new PayrollCalculationService.Calculation(null,t,start,end,List.of(),zero,Map.of(),Map.of(),"Bereits zugeordnete Einmalzahlung",null,deltaBasis,List.of(),null);
            entries.add(new PayrollLedgerService.Entry(new Payslip(),new Payslip(),c));
        }
        var b=result.getGermanBasis();var prior=assignment==null?null:new GermanPayrollTax.PriorYearBasis(2025,assignment,priorPension,priorHealth,priorSource);
        return new TaxCalculationService.Result(round(employee),round(employer),"SV 2026; Einmalzahlungen nach tatsächlichem Zuflussmonat")
                .withGermanBasis(new GermanPayrollTax.Basis(b.insuranceDays(),b.pensionBase().add(currentPension),b.healthBase().add(currentHealth),null,prior));
    }
    private static boolean extraContribution(String key){return !key.startsWith("Income tax")&&!key.startsWith("Solidarity surcharge")&&!key.startsWith("Church tax")&&(key.endsWith(" · thirteenth")||key.endsWith(" · SV 2025"));}
    private static Map<String,Double> round(Map<String,Double> input){Map<String,Double> output=new LinkedHashMap<>();input.forEach((k,v)->output.put(k,BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).doubleValue()));return output;}
}
