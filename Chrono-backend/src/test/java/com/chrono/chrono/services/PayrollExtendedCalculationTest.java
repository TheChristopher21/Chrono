package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.services.payroll.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollExtendedCalculationTest {
    static BigDecimal n(String v) { return new BigDecimal(v); }
    static PayrollTerms terms(String canton, String frequency) {
        return PayrollTestTerms.change(PayrollTestTerms.swiss(), Map.of("entryDate", "2026-01-01", "taxMode", "CH_SOURCE", "canton", canton, "tariffCode", "A0N",
                "contract", Map.of("salaryProration", "THIRTY_DAYS", "pensionProration", "THIRTY_DAYS", "thirteenthFrequency", frequency)));
    }
    static PayrollGrossCalculator.Result gross(String base) { return new PayrollGrossCalculator.Result(n(base), BigDecimal.ZERO, BigDecimal.ZERO, n(base), 0); }
    static PayrollLedgerService.History empty() { return new PayrollLedgerService.History(List.of(), List.of(), true); }
    static PayrollConfigurationService.Coverage coverage() { return new PayrollContractCalculationTest().coverage(false); }
    static PayrollLedgerService.Entry entry(int month, PayrollTerms t, PayrollGrossCalculator.Result g, TaxCalculationService.Result result) {
        LocalDate start = LocalDate.of(2026, month, 1), end = start.withDayOfMonth(start.lengthOfMonth());
        var c = new PayrollCalculationService.Calculation(1L, t, start, end, List.of(), g, result.getEmployee(), result.getEmployer(), result.getConfigurationReference(), result.getSwissBasis(), result.getGermanBasis(), List.of(), null);
        return new PayrollLedgerService.Entry(new Payslip(), new Payslip(), c);
    }
    static PayrollLedgerService.History history(List<PayrollLedgerService.Entry> entries) { return new PayrollLedgerService.History(List.copyOf(entries), List.of(), true); }
    @Test void statutorySwissDaysIncludePublishedFebruaryAnd31stExceptions() {
        for (String[] sample : new String[][]{{"2023-03-31","2023-03-31","1"},{"2023-03-30","2023-03-31","1"},{"2023-03-29","2023-03-31","2"},
                {"2024-02-29","2024-02-29","1"},{"2024-02-28","2024-02-29","1"},{"2023-02-27","2023-02-28","4"},
                {"2024-02-01","2024-02-28","30"},{"2023-02-01","2023-02-27","27"},{"2026-01-16","2026-03-15","60"}})
            assertEquals(Integer.parseInt(sample[2]), PayrollPeriod.swissDays(LocalDate.parse(sample[0]), LocalDate.parse(sample[1])), Arrays.toString(sample));
    }
    @Test void calendarSalaryAndStatutoryDaysAreDistinctAndPartialBvgIsExplicit() {
        var t = PayrollTestTerms.change(terms("ZH","NONE"), Map.of("entryDate","2026-01-16"));
        LocalDate start=LocalDate.of(2026,1,16), end=LocalDate.of(2026,1,31);
        assertEquals(n("2580.65"), PayrollPeriod.prorate(n("5000"),start,end,"CALENDAR_DAYS"));
        var g=PayrollGrossCalculator.calculate(t,start,end,List.of(),null,false,null);
        assertEquals(n("2500.00"),g.base());
        var tax=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,start,end,g,empty());
        assertEquals(138.0,tax.getEmployee().get("Withholding tax")); // ZH A0N, rate income 5,000 at 5.52%
        assertEquals(120.25,tax.getEmployee().get("BVG"));
        assertEquals(15,tax.getSwissBasis().insuranceDays());
        assertDoesNotThrow(()->TaxCalculationService.validateSupportedPeriod(t,start,end));
        assertThrows(IllegalStateException.class,()->TaxCalculationService.validateSupportedPeriod(t,start.plusDays(1),end));
    }
    @ParameterizedTest @ValueSource(strings={"FR","GE","TI","VD","VS"})
    void readsAllAnnualCantons(String canton) { assertTrue(new SwissSourceTax().annual(2026,canton,"A0N",n("5000"),n("60000")).amount().signum()>0); }
    @Test void independentVaud2026PdfAnnualBandBoundsAreNotMonthlyTruncation() {
        // VD 21034-10_Employeurs_2026.pdf, page 20, A0: 59,401–60,000 = 8.17%; 60,001–60,600 = 8.34%.
        var tax=new SwissSourceTax();
        assertEquals(n("408.50"),tax.annual(2026,"VD","A0N",n("5000"),n("60000.99")).amount());
        assertEquals(n("417.00"),tax.annual(2026,"VD","A0N",n("5000"),n("60001")).amount());
        assertEquals(n("479.00"),tax.annual(2026,"VD","A0N",n("5000"),n("65000")).amount());
        // Geneva uses lower-inclusive x,000 boundaries (official 2026 A0/B/C/H table).
        assertEquals(n("0.0749"),tax.annual(2026,"GE","A0N",n("5000"),n("59999.99")).rate());
        assertEquals(n("0.0762"),tax.annual(2026,"GE","A0N",n("5000"),n("60000")).rate());
    }
    @Test void annualSalaryChangeReconcilesPreviousWithholdingWithoutMutatingJanuary() {
        var tax=new SwissSourceTax(); var jan=terms("VD","NONE");
        var first=SwissPayrollSettlement.calculate(tax,coverage(),jan,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),gross("5000"),empty());
        var h=history(List.of(entry(1,jan,gross("5000"),first)));
        var feb=PayrollTestTerms.change(jan,Map.of("amount","6000"));
        var second=SwissPayrollSettlement.calculate(tax,coverage(),feb,LocalDate.of(2026,2,1),LocalDate.of(2026,2,28),gross("6000"),h);
        assertEquals(0,n("71000").compareTo(second.getSwissBasis().annualRateIncome()));
        // VD A0 70,801–71,400 = 11.04%; 11,000*11.04% - 408.50 = 805.90.
        assertEquals(805.90,second.getEmployee().get("Withholding tax"));
        assertEquals(408.50,first.getEmployee().get("Withholding tax"));
    }
    @Test void knownThirteenthIsForecastButOnlyPaidAtContractDateAndAnnualTaxTotalsReconcile() {
        var t=terms("VD","ANNUAL"); var source=new SwissSourceTax();
        var ledger=new PayrollLedgerService(mock(com.chrono.chrono.repositories.PayslipRepository.class),new ObjectMapper());
        List<PayrollLedgerService.Entry> entries=new ArrayList<>(); BigDecimal paid=BigDecimal.ZERO, withheld=BigDecimal.ZERO;
        for (int month=1;month<=12;month++) {
            LocalDate start=LocalDate.of(2026,month,1),end=start.withDayOfMonth(start.lengthOfMonth());
            var h=history(entries); var g=gross("5000").withThirteenth(ledger.thirteenth(t,start,end,gross("5000"),h));
            if (month<12) assertEquals(0,g.thirteenthAmount().signum()); else assertEquals(0,n("5000").compareTo(g.thirteenthAmount()));
            var result=SwissPayrollSettlement.calculate(source,coverage(),t,start,end,g,h);
            assertEquals(0,n("65000").compareTo(result.getSwissBasis().annualRateIncome()));
            withheld=withheld.add(BigDecimal.valueOf(result.getEmployee().get("Withholding tax"))); paid=paid.add(g.thirteenthAmount()); entries.add(entry(month,t,g,result));
        }
        assertEquals(0,n("5000").compareTo(paid)); assertEquals(0,n("6227").compareTo(withheld));
    }
    @Test void monthlyThirteenthAccrualRoundingCannotAccumulateOrPayTwice() {
        var t=terms("ZH","MONTHLY"); var ledger=new PayrollLedgerService(mock(com.chrono.chrono.repositories.PayslipRepository.class),new ObjectMapper());
        List<PayrollLedgerService.Entry> entries=new ArrayList<>(); BigDecimal paid=BigDecimal.ZERO;
        for(int month=1;month<=12;month++) {
            LocalDate start=LocalDate.of(2026,month,1),end=start.withDayOfMonth(start.lengthOfMonth());
            var extra=ledger.thirteenth(t,start,end,gross("5000"),history(entries));
            assertEquals(0,extra.remainder(n("0.05")).signum());
            paid=paid.add(extra); entries.add(entry(month,t,gross("5000").withThirteenth(extra),new TaxCalculationService.Result(Map.of(),Map.of())));
        }
        assertEquals(0,n("5000").compareTo(paid));
    }
    @Test void missingAnnualHistoryNeverBecomesAnAssumedZero() {
        var incomplete=new PayrollLedgerService.History(List.of(),List.of(),false);
        assertThrows(IllegalStateException.class,()->SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),terms("VD","NONE"),LocalDate.of(2026,2,1),LocalDate.of(2026,2,28),gross("5000"),incomplete));
    }
    @Test void removingThirteenthCannotEraseAnUnpaidHistoricalEntitlement() {
        var ledger = new PayrollLedgerService(mock(com.chrono.chrono.repositories.PayslipRepository.class), new ObjectMapper());
        var prior = entry(1, terms("ZH", "ANNUAL"), gross("5000"), new TaxCalculationService.Result(Map.of(), Map.of()));
        var error = assertThrows(IllegalStateException.class, () -> ledger.thirteenth(terms("ZH", "NONE"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), gross("5000"), history(List.of(prior))));
        assertTrue(error.getMessage().contains("noch nicht ausgeglichen"));
        assertEquals(0, prior.calculation().gross().thirteenthAmount().signum());
    }
    @Test void removingFullySettledMonthlyThirteenthDoesNotAccrueAnotherInstallment() {
        var ledger = new PayrollLedgerService(mock(com.chrono.chrono.repositories.PayslipRepository.class), new ObjectMapper());
        var prior = entry(1, terms("ZH", "MONTHLY"), gross("5000").withThirteenth(n("416.65")),
                new TaxCalculationService.Result(Map.of(), Map.of()));
        assertEquals(0, ledger.thirteenth(terms("ZH", "NONE"), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28), gross("5000"), history(List.of(prior))).signum());
        assertThrows(IllegalStateException.class, () -> ledger.thirteenth(terms("ZH", "NONE"), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28), gross("5000"), new PayrollLedgerService.History(List.of(prior), List.of(), false)));
    }
    @Test void exitSettlesThirteenthAndUsesContractCycleForMonthlySourceTax() {
        var t=PayrollTestTerms.change(terms("ZH","SEMIANNUAL"),Map.of("amount","6000","contract",Map.of("exitDate","2026-06-15","salaryProration","THIRTY_DAYS","pensionProration","THIRTY_DAYS","thirteenthFrequency","SEMIANNUAL")));
        var ledger=new PayrollLedgerService(mock(com.chrono.chrono.repositories.PayslipRepository.class),new ObjectMapper());
        List<PayrollLedgerService.Entry> entries=new ArrayList<>();
        for(int month=1;month<=5;month++) {
            LocalDate a=LocalDate.of(2026,month,1), b=a.withDayOfMonth(a.lengthOfMonth());
            var result=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,a,b,gross("6000"),history(entries));
            entries.add(entry(month,t,gross("6000"),result));
        }
        LocalDate a=LocalDate.of(2026,6,1), b=LocalDate.of(2026,6,15);
        var extra=ledger.thirteenth(t,a,b,gross("3000"),history(entries)); assertEquals(0,n("2750").compareTo(extra));
        var result=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,a,b,gross("3000").withThirteenth(extra),history(entries));
        // KS45 6.6 example 3: 3,000/15*30 + 2,750/165*180 = 9,000.
        assertEquals(0,n("9000").compareTo(result.getSwissBasis().monthlyRateIncome()));
    }
    @Test void swissAnnualInsuranceCeilingAllowsAccumulatedHeadroomOnThirteenth() {
        var t=PayrollTestTerms.change(terms("ZH","ANNUAL"),Map.of("amount","10000"));
        var previous=entry(1,t,gross("10000"),SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),gross("10000"),empty()));
        var result=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,LocalDate.of(2026,2,1),LocalDate.of(2026,2,28),gross("10000").withThirteenth(n("5000")),history(List.of(previous)));
        assertEquals(0,n("14700").compareTo(result.getSwissBasis().alvUvgBase())); // 24,700 YTD ceiling minus 10,000 already insured
        assertEquals(161.70,result.getEmployee().get("ALV"));
    }
}
