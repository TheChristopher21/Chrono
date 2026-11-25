package com.chrono.chrono.services.payroll;
import com.chrono.chrono.services.PayrollCalculationService;
import java.time.LocalDate;
import java.util.List;

/** Immutable independent cash, withholding and social-insurance ledgers for a month. */
public record PayrollRemuneration(PayrollGrossCalculator.Result taxGross,PayrollGrossCalculator.Result socialGross,
        List<PaymentReference> applied,List<PlanReference> plan,boolean cashOnly,PayrollHistoricalAssessment assessment) {
    public PayrollRemuneration(PayrollGrossCalculator.Result tax,PayrollGrossCalculator.Result social,List<PaymentReference> applied,List<PlanReference> plan,boolean cashOnly){this(tax,social,applied,plan,cashOnly,null);}
    public PayrollRemuneration withAssessment(PayrollHistoricalAssessment value){return new PayrollRemuneration(taxGross,socialGross,applied,plan,cashOnly,value);}
    public record PaymentReference(String domain,Long versionId,String source,PayrollPayment payment,PayrollPeriodData.GermanPriorYearSocial priorSocial,PayrollPeriodData.GermanConcurrentIncome concurrent) {
        public PaymentReference(String domain,Long id,String source,PayrollPayment payment){this(domain,id,source,payment,null,null);}
    }
    public record PlanReference(Long versionId,LocalDate start,LocalDate end) {}
    public static PayrollGrossCalculator.Result tax( PayrollCalculationService.Calculation c){return c.remuneration()==null?c.gross():c.remuneration().taxGross();}
    public static PayrollGrossCalculator.Result social(PayrollCalculationService.Calculation c){return c.remuneration()==null?c.gross():c.remuneration().socialGross();}
}
