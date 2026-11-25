package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class PayrollPaymentLedgerService {
    private final PayrollPeriodInputRepository versions;private final ObjectMapper json;
    public PayrollPaymentLedgerService(PayrollPeriodInputRepository versions,ObjectMapper json){this.versions=versions;this.json=json;}
    public record Item(Long versionId,String source,PayrollPayment payment,PayrollPeriodData.GermanPriorYearSocial priorSocial,PayrollPeriodData.GermanConcurrentIncome concurrent) {
        public Item(Long id,String source,PayrollPayment payment){this(id,source,payment,null,null);}
    }
    public record Plan(List<Item> payments,List<PayrollRemuneration.PlanReference> references) {}
    public record Applied(PayrollGrossCalculator.Result cash,PayrollRemuneration remuneration) {}
    public Plan year(User user,LocalDate date) {
        if(user.getCompany()==null)return new Plan(List.of(),List.of());
        var first=date.withDayOfYear(1);var last=first.withMonth(12).withDayOfMonth(31);
        boolean writing=TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows=writing?versions.yearForUpdate(user.getCompany().getId(),user.getId(),first,last):versions.year(user.getCompany().getId(),user.getId(),first,last);
        Set<String> periods=new HashSet<>(),references=new HashSet<>();List<Item> payments=new ArrayList<>();List<PayrollRemuneration.PlanReference> refs=new ArrayList<>();
        for(var row:rows) {
            if(!periods.add(row.getPeriodStart()+":"+row.getPeriodEnd()))continue;
            refs.add(new PayrollRemuneration.PlanReference(row.getId(),row.getPeriodStart(),row.getPeriodEnd()));
            try {var data=json.readValue(row.getParameters(),PayrollPeriodData.class);if(data.payments()!=null)for(var payment:data.payments()) {
                if(!references.add(payment.reference()))throw new IllegalStateException("Nachzahlungsreferenz ist im selben Jahr mehrfach erfasst: "+payment.reference());
                payments.add(new Item(row.getId(),row.getSource(),payment,data.germanPriorYearSocial(),data.germanConcurrentIncome()));
            }}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Nachzahlungsgrundlagen sind nicht lesbar.",e);}
        }
        payments.sort(Comparator.comparing(i->i.payment().reference()));refs.sort(Comparator.comparing(PayrollRemuneration.PlanReference::start).thenComparing(PayrollRemuneration.PlanReference::versionId));
        return new Plan(List.copyOf(payments),List.copyOf(refs));
    }
    public static LocalDate taxMonth(PayrollPayment payment,PayrollTerms t) {
        if(germanRegularTax(payment,t))return payment.earnedFrom().withDayOfMonth(1);
        if("CH".equals(t.country())&&t.exitDate()!=null&&payment.receivedOn().isAfter(t.exitDate())&&!payment.claimEstablishedOn().isAfter(t.exitDate()))return t.exitDate().withDayOfMonth(1);
        return payment.receivedOn().withDayOfMonth(1);
    }
    public static LocalDate socialMonth(PayrollPayment payment,PayrollTerms t) {
        if("RETROACTIVE_RAISE".equals(payment.kind()))return (t.exitDate()!=null&&payment.claimEstablishedOn().isAfter(t.exitDate())?t.exitDate():payment.claimEstablishedOn()).withDayOfMonth(1);
        if("CH".equals(t.country())&&payment.earnedFrom().getYear()==2025&&(t.exitDate()==null||!payment.receivedOn().isAfter(t.exitDate())))return payment.receivedOn().withDayOfMonth(1);
        if("REGULAR_ARREARS".equals(payment.kind())||"CH".equals(t.country()))return payment.earnedFrom().withDayOfMonth(1);
        return t.exitDate()!=null&&payment.receivedOn().isAfter(t.exitDate())?t.exitDate().withDayOfMonth(1):payment.receivedOn().withDayOfMonth(1);
    }
    public static List<PayrollRemuneration.PaymentReference> assigned(Plan plan,PayrollTerms t,LocalDate date,LocalDate end,boolean cashOnly) {
        List<PayrollRemuneration.PaymentReference> refs=new ArrayList<>();YearMonth month=YearMonth.from(date);
        for(var item:plan.payments()) {
            var p=item.payment();boolean receipt=!p.receivedOn().isBefore(date)&&!p.receivedOn().isAfter(end);
            if(receipt)refs.add(reference("CASH",item));
            boolean originalTax=germanRegularTax(p,t)||"CH".equals(t.country())&&t.exitDate()!=null&&p.receivedOn().isAfter(t.exitDate())&&!p.claimEstablishedOn().isAfter(t.exitDate());
            if(originalTax?!cashOnly&&YearMonth.from(taxMonth(p,t)).equals(month):receipt)refs.add(reference("TAX",item));
            if(!cashOnly&&YearMonth.from(socialMonth(p,t)).equals(month))refs.add(reference("SOCIAL",item));
            if(receipt&&(taxMonth(p,t).getYear()==2025||socialMonth(p,t).getYear()==2025))refs.add(reference("HISTORICAL",item));
        }
        return List.copyOf(refs);
    }
    private static PayrollRemuneration.PaymentReference reference(String domain,Item i){return new PayrollRemuneration.PaymentReference(domain,i.versionId(),i.source(),i.payment(),i.priorSocial(),i.concurrent());}
    public static void requireHistory(Plan plan,PayrollTerms t,PayrollLedgerService.History history) {
        for(var entry:history.entries()) {
            var c=entry.calculation();var expected=assigned(plan,t,c.periodStart(),c.periodEnd(),c.remuneration()!=null&&c.remuneration().cashOnly());
            var actual=c.remuneration()==null?List.of():c.remuneration().applied();
            if(!new HashSet<>(expected).equals(new HashSet<>(actual)))throw new IllegalStateException("Nachzahlung verändert die Steuer-/SV-Zuordnung der Vorperiode "+c.periodStart()+". Den vollständigen Korrekturlauf ab dieser Periode zuerst freigeben; Originalbelege bleiben erhalten.");
        }
    }
    public static Applied apply(Plan plan,PayrollTerms t,LocalDate start,LocalDate end,PayrollGrossCalculator.Result base,boolean cashOnly) {
        var refs=assigned(plan,t,start,end,cashOnly);BigDecimal cash=BigDecimal.ZERO,taxRegular=BigDecimal.ZERO,taxOther=BigDecimal.ZERO,socialRegular=BigDecimal.ZERO,socialOther=BigDecimal.ZERO;
        for(var ref:refs) {
            var p=ref.payment();switch(ref.domain()) {
                case "CASH"->cash=cash.add(p.gross());
                case "TAX"->{if(germanRegularTax(p,t))taxRegular=taxRegular.add(p.gross());else taxOther=taxOther.add(p.gross());}
                case "SOCIAL"->{if(regular(p)||"CH".equals(t.country()))socialRegular=socialRegular.add(p.gross());else socialOther=socialOther.add(p.gross());}
                case "HISTORICAL"->{}
                default->throw new IllegalStateException("Unbekannte Nachzahlungszuordnung.");
            }
        }
        var cashGross=add(base,cash,BigDecimal.ZERO,true,false);var taxGross=add(base,taxRegular,taxOther,false,"CH".equals(t.country()));var socialGross=add(base,socialRegular,socialOther,false,false);
        return new Applied(cashGross,new PayrollRemuneration(taxGross,socialGross,refs,plan.references(),cashOnly));
    }
    public static boolean regular(PayrollPayment p){return "REGULAR_ARREARS".equals(p.kind())||"RETROACTIVE_RAISE".equals(p.kind());}
    public static boolean germanRegularTax(PayrollPayment p,PayrollTerms t) {return "DE".equals(t.country())&&regular(p)&&(p.earnedFrom().getYear()==2026||!p.receivedOn().isAfter(LocalDate.of(2026,1,21)));}
    private static PayrollGrossCalculator.Result add(PayrollGrossCalculator.Result base,BigDecimal regular,BigDecimal other,boolean cash,boolean swissOther) {
        Map<String,BigDecimal> extra=new LinkedHashMap<>(base.extraEarnings());BigDecimal wage=base.base(),premium=base.premium(),thirteenth=base.thirteenthAmount();
        if(cash&&regular.signum()!=0)extra.put("Arrears payment",regular);
        else {wage=wage.add(regular);if(swissOther)premium=premium.add(other);else thirteenth=thirteenth.add(other);}
        return new PayrollGrossCalculator.Result(wage,premium,base.payout(),base.total().add(regular).add(other),base.paidOvertimeMinutes(),thirteenth,extra,base.eligibleThirteenthBase());
    }
}
