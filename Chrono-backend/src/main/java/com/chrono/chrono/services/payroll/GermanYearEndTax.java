package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.PayrollCalculationService;
import java.math.*;
import java.time.*;
import java.util.*;

/** EStG 41c(3)/42b: post-year withholding uses the annual PAP, not twelve monthly refunds. */
public final class GermanYearEndTax {
    private GermanYearEndTax() {}
    public static Map<String,BigDecimal> calculate(List<PayrollCalculationService.Calculation> year) {
        if(year.size()!=12) throw unsupported();
        var last=year.get(11); PayrollTerms t=last.terms(); int taxYear=last.periodEnd().getYear();
        if(taxYear!=2026 || t.entryDate().isAfter(LocalDate.of(taxYear,1,1)) || t.exitDate()!=null&&t.exitDate().isBefore(LocalDate.of(taxYear,12,31))) throw unsupported();
        BigDecimal gross=BigDecimal.ZERO;
        for(int month=1;month<=12;month++) {
            var c=year.get(month-1); var p=c.terms();
            if(!c.periodStart().equals(LocalDate.of(taxYear,month,1)) || !PayrollPeriod.fullMonth(c.periodStart(),c.periodEnd())
                    || c.germanBasis()==null || c.germanBasis().insuranceDays()!=30 || p.multipleEmployment()!=null
                    || !sameTaxCharacteristics(t,p) || p.taxClass()>4 || p.taxAllowance().signum()!=0 || p.taxAddition().signum()!=0
                    || p.taxFactor().compareTo(BigDecimal.ONE)!=0 || !p.parent()&&p.birthDate().getYear()+23==taxYear) throw unsupported();
            gross=gross.add(c.gross().total());
        }
        var pap=new BmfLohnsteuer2026(); pap.LZZ=1; pap.RE4=gross.movePointRight(2); pap.STKL=t.taxClass();
        pap.af=0; pap.f=1; pap.ZKF=t.childAllowance(); pap.R=t.churchRate().signum()>0?1:0;
        pap.KVZ=t.healthAdditionalRate().movePointRight(2); pap.PVS=t.saxony()?1:0;
        pap.PVZ=!t.parent()&&!last.periodEnd().isBefore(t.birthDate().plusYears(23).withDayOfMonth(1).plusMonths(1))?1:0;
        pap.PVA=BigDecimal.valueOf(t.parent()?Math.min(4,Math.max(0,t.childrenUnder25()-1)):0);
        pap.calculate();
        return Map.of("Income tax",pap.LSTLZZ.movePointLeft(2),"Solidarity surcharge",pap.SOLZLZZ.movePointLeft(2),
                "Church tax",pap.BK.movePointLeft(2).multiply(t.churchRate()).setScale(2,RoundingMode.HALF_UP));
    }
    private static boolean sameTaxCharacteristics(PayrollTerms a,PayrollTerms b) {
        return Objects.equals(a.country(),b.country())&&Objects.equals(a.taxClass(),b.taxClass())&&a.childAllowance().compareTo(b.childAllowance())==0
                && a.churchRate().compareTo(b.churchRate())==0 && a.healthAdditionalRate().compareTo(b.healthAdditionalRate())==0
                && Objects.equals(a.parent(),b.parent())&&Objects.equals(a.childrenUnder25(),b.childrenUnder25())&&Objects.equals(a.saxony(),b.saxony())
                && Objects.equals(a.birthDate(),b.birthDate());
    }
    private static IllegalStateException unsupported() {return new IllegalStateException("Deutscher Jahresausgleich: vollständiges Kalenderjahr ohne Unterbrechung, unveränderte Steuer-/Versicherungsmerkmale und zulässige Klasse I–IV ohne Faktor/Frei-/Hinzurechnungsbetrag erforderlich. Ausschlussfälle nach § 42b EStG gesondert bearbeiten.");}
}
