package com.chrono.chrono.services;

import com.chrono.chrono.services.payroll.GermanPayrollTax;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GermanPayrollTaxTest {
    final LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
    @Test void partialMonthUsesDailyPAPAndCalendarSvDaysInsteadOfFullMonthlyCaps() {
        var t=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("amount","10000"));
        var result=GermanPayrollTax.calculateRun(t,PayrollExtendedCalculationTest.gross("5000"),LocalDate.of(2026,1,16),end,PayrollExtendedCalculationTest.empty());
        // Independent BMF daily RE4=31250: LST 79.00, Soli 2.67. Sixteen calendar days.
        assertEquals(1264.0,result.getEmployee().get("Income tax")); assertEquals(42.72,result.getEmployee().get("Solidarity surcharge"));
        assertEquals(419.12,result.getEmployee().get("Pension insurance")); // 8,450 /30 *16 *9.3%
        assertEquals(271.25,result.getEmployee().get("Health insurance")); // 5,812.50 /30 *16 *8.75%
        assertEquals(16,result.getGermanBasis().insuranceDays());
    }
    @Test void decemberThirteenthUsesIndependentOneOffPAPAndAnnualSocialHeadroom() {
        var t=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("entryDate","2026-01-01","contract",Map.of("salaryProration","CALENDAR_DAYS","pensionProration","FULL","thirteenthFrequency","ANNUAL","germanPreviousEmploymentGross",0)));
        var entries=new java.util.ArrayList<PayrollLedgerService.Entry>();
        for(int month=1;month<=11;month++) {
            var a=LocalDate.of(2026,month,1); var b=a.withDayOfMonth(a.lengthOfMonth());
            var result=GermanPayrollTax.calculateRun(t,PayrollExtendedCalculationTest.gross("5000"),a,b,PayrollExtendedCalculationTest.history(entries));
            entries.add(PayrollExtendedCalculationTest.entry(month,t,PayrollExtendedCalculationTest.gross("5000"),result));
        }
        var result=GermanPayrollTax.calculateRun(t,PayrollExtendedCalculationTest.gross("5000").withThirteenth(new BigDecimal("5000")),LocalDate.of(2026,12,1),LocalDate.of(2026,12,31),PayrollExtendedCalculationTest.history(entries));
        assertEquals(782.41,result.getEmployee().get("Income tax")); assertEquals(1383.0,result.getEmployee().get("Income tax · thirteenth"));
        assertEquals(465.0,result.getEmployee().get("Pension insurance · thirteenth"));
        assertEquals(437.50,result.getEmployee().get("Health insurance · thirteenth"));
        assertEquals(50.0,result.getEmployer().get("U1")); assertFalse(result.getEmployer().containsKey("U1 · thirteenth"));
        assertEquals(0,new BigDecimal("60000").compareTo(result.getGermanBasis().projectedAnnualGross()));
    }
    @Test void oneOffForecastUsesContractualCalendarProrationOfKnownFutureExitMonth() {
        var t=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("entryDate","2026-01-01","contract",Map.of(
                "exitDate","2026-07-15","salaryProration","CALENDAR_DAYS","pensionProration","FULL",
                "thirteenthFrequency","SEMIANNUAL","germanPreviousEmploymentGross",0)));
        var entries=new java.util.ArrayList<PayrollLedgerService.Entry>();
        for(int month=1;month<=5;month++) {
            var a=LocalDate.of(2026,month,1); var b=a.withDayOfMonth(a.lengthOfMonth());
            var result=GermanPayrollTax.calculateRun(t,PayrollExtendedCalculationTest.gross("5000"),a,b,PayrollExtendedCalculationTest.history(entries));
            entries.add(PayrollExtendedCalculationTest.entry(month,t,PayrollExtendedCalculationTest.gross("5000"),result));
        }
        var result=GermanPayrollTax.calculateRun(t,PayrollExtendedCalculationTest.gross("5000").withThirteenth(new BigDecimal("2500")),
                LocalDate.of(2026,6,1),LocalDate.of(2026,6,30),PayrollExtendedCalculationTest.history(entries));
        // Six full months plus 5,000 * 15/31 for July; the future exit settlement is not part of JRE4.
        assertEquals(0,new BigDecimal("32419.35").compareTo(result.getGermanBasis().projectedAnnualGross()));
        // Independently retrieved from the official BMF 2026Version1 test interface on 2026-10-20:
        // LZZ=2, RE4=500000, JRE4=3241935, SONSTB=250000, STKL=1, KVZ=2.9, PVZ=1.
        assertEquals(782.41,result.getEmployee().get("Income tax"));
        assertEquals(532.0,result.getEmployee().get("Income tax · thirteenth"));
        assertEquals(0.0,result.getEmployee().get("Solidarity surcharge · thirteenth"));
    }
    @Test void mapsTheStandardEmployeeToTheIndependentBmfReference() {
        var result=GermanPayrollTax.calculate(PayrollTestTerms.german(),new BigDecimal("5000"),start,end);
        assertEquals(782.41,result.getEmployee().get("Income tax"));
        assertEquals(0.0,result.getEmployee().get("Solidarity surcharge"));
    }
    @Test void applies2026CeilingsAndFullHealthAdditionalRateSplitEqually() {
        var result=GermanPayrollTax.calculate(PayrollTestTerms.german(),new BigDecimal("10000"),start,end);
        assertEquals(785.85,result.getEmployee().get("Pension insurance"));
        assertEquals(109.85,result.getEmployee().get("Unemployment insurance"));
        assertEquals(508.59,result.getEmployee().get("Health insurance"));
        assertEquals(139.50,result.getEmployee().get("Nursing insurance"));
        assertEquals(104.63,result.getEmployer().get("Nursing insurance"));
        assertTrue(result.getEmployee().get("Income tax")>2000);
    }
    @Test void distinguishesLifetimeParentStatusFromChildrenUnder25AndSaxony() {
        var terms=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("parent",true,"childrenUnder25",3,"saxony",true));
        var result=GermanPayrollTax.calculate(terms,new BigDecimal("5000"),start,end);
        assertEquals(90.0,result.getEmployee().get("Nursing insurance")); // (2.3%-2x0.25%) x 5,000
        assertEquals(65.0,result.getEmployer().get("Nursing insurance"));
        terms=PayrollTestTerms.change(terms,Map.of("childrenUnder25",0));
        assertEquals(115.0,GermanPayrollTax.calculate(terms,new BigDecimal("5000"),start,end).getEmployee().get("Nursing insurance"));
    }
    @Test void childlessSurchargeStartsAfter23rdBirthdayMonthNotAtTodaysAge() {
        var terms=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("birthDate","2003-01-15"));
        assertEquals(90.0,GermanPayrollTax.calculate(terms,new BigDecimal("5000"),start,end).getEmployee().get("Nursing insurance"));
        assertEquals(120.0,GermanPayrollTax.calculate(terms,new BigDecimal("5000"),start.plusMonths(1),end.plusMonths(1)).getEmployee().get("Nursing insurance"));
    }
    @Test void refusesMiniMidiAndUnknownYearsInsteadOfGuessing() {
        assertThrows(IllegalStateException.class,()->GermanPayrollTax.calculate(PayrollTestTerms.german(),new BigDecimal("2000"),start,end));
        assertThrows(IllegalStateException.class,()->GermanPayrollTax.calculate(PayrollTestTerms.german(),new BigDecimal("5000"),start.minusYears(1),end.minusYears(1)));
    }
}
