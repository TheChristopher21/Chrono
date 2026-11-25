package com.chrono.chrono.services;

import com.chrono.chrono.services.payroll.*;
import com.chrono.chrono.entities.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.chrono.chrono.services.PayrollExtendedCalculationTest.*;

class PayrollSpecialCasesTest {
    final LocalDate january=LocalDate.of(2026,1,1),januaryEnd=LocalDate.of(2026,1,31);
    PayrollTerms multiple(PayrollTerms t,String method,String own,String total,String other) {
        Map<String,Object> contract=new LinkedHashMap<>(); contract.put("salaryProration","THIRTY_DAYS"); contract.put("pensionProration","FULL"); contract.put("thirteenthFrequency","NONE");
        Map<String,Object> m=new LinkedHashMap<>();m.put("method",method); if(own!=null)m.put("ownPercent",own);if(total!=null)m.put("totalPercent",total);if(other!=null)m.put("otherPeriodicMonthlyGross",other);
        contract.put("multipleEmployment",m);
        return PayrollTestTerms.change(t,Map.of("amount","3000","monthlySingleEmployment",false,"contract",contract));
    }
    @Test void swissMultipleEmploymentScalesOnlyPeriodicIncomeAndRetainsTheOwnTaxableWage() {
        var t=multiple(terms("ZH","NONE"),"TOTAL_PERCENT","50","100",null);
        PayrollTermsService.validate(t);
        var g=new PayrollGrossCalculator.Result(n("3000"),n("200"),BigDecimal.ZERO,n("3200"),0);
        var r=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,january,januaryEnd,g,empty());
        assertMoney("6200",r.getSwissBasis().monthlyRateIncome()); // KS45 6.4: 3,000 /50% + own aperiodic 200
        assertMoney("3200",r.getSwissBasis().withholdingAllocations().get(0).taxableBase());
    }
    @Test void annualMultipleEmploymentUsesKnownOrUnknownTotalPercentage() {
        for(String method:List.of("TOTAL_PERCENT","UNKNOWN_TOTAL_PERCENT")) {
            var t=multiple(terms("VD","NONE"),method,"50",method.equals("TOTAL_PERCENT")?"90":null,null);
            var r=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,january,januaryEnd,gross("3000"),empty());
            assertMoney(method.equals("TOTAL_PERCENT")?"64800":"72000",r.getSwissBasis().annualRateIncome());
        }
    }
    @Test void knownOtherIncomeRequiresExplicitPeriodDataAndDoesNotForecastUnknownBonuses() {
        var t=multiple(terms("VD","NONE"),"KNOWN_OTHER_INCOME",null,null,"2000");
        var g=new PayrollGrossCalculator.Result(n("3000"),n("200"),BigDecimal.ZERO,n("3200"),0);
        assertThrows(IllegalStateException.class,()->SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,january,januaryEnd,g,empty()));
        var r=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,january,januaryEnd,g,empty(),new PayrollPeriodData(List.of(),null,n("500")));
        assertMoney("60700",r.getSwissBasis().annualRateIncome());
    }
    @Test void cantonChangeClosesThePreviousPeriodAndNeverImportsItsBonusIntoTheNewCanton() {
        var source=new SwissSourceTax();var vd=terms("VD","NONE");var ge=terms("GE","NONE");
        var bonus=new PayrollGrossCalculator.Result(n("5000"),n("30000"),BigDecimal.ZERO,n("35000"),0);
        var jan=SwissPayrollSettlement.calculate(source,coverage(),vd,january,januaryEnd,bonus,empty());
        var h=history(List.of(entry(1,vd,bonus,jan)));
        var feb=SwissPayrollSettlement.calculate(source,coverage(),ge,january.plusMonths(1),LocalDate.of(2026,2,28),gross("5000"),h);
        assertMoney("60000",feb.getSwissBasis().annualRateIncome());
        var old=feb.getSwissBasis().withholdingAllocations().get(0);
        assertEquals("VD",old.canton());assertMoney("90000",old.rateIncome());assertMoney("0",old.amount());
        assertEquals("GE",feb.getSwissBasis().withholdingAllocations().get(1).canton());
    }
    @Test void oldCantonClosingAdjustmentIsAttributedOnceAndLaterSalaryDoesNotReopenIt() {
        var source=new SwissSourceTax();var vd=terms("VD","NONE");var raised=PayrollTestTerms.change(vd,Map.of("amount","10000"));var ge=terms("GE","NONE");
        var jan=SwissPayrollSettlement.calculate(source,coverage(),vd,january,januaryEnd,gross("5000"),empty());
        var entries=new ArrayList<PayrollLedgerService.Entry>();entries.add(entry(1,vd,gross("5000"),jan));
        var feb=SwissPayrollSettlement.calculate(source,coverage(),raised,january.plusMonths(1),LocalDate.of(2026,2,28),gross("10000"),history(entries));
        entries.add(entry(2,raised,gross("10000"),feb));
        var march=SwissPayrollSettlement.calculate(source,coverage(),ge,LocalDate.of(2026,3,1),LocalDate.of(2026,3,31),gross("5000"),history(entries));
        var closing=march.getSwissBasis().withholdingAllocations().get(0);
        assertMoney("90000",closing.rateIncome()); assertTrue(closing.amount().signum()<0);
        entries.add(entry(3,ge,gross("5000"),march));
        var april=SwissPayrollSettlement.calculate(source,coverage(),PayrollTestTerms.change(ge,Map.of("amount","6000")),LocalDate.of(2026,4,1),LocalDate.of(2026,4,30),gross("6000"),history(entries));
        assertMoney("0",april.getSwissBasis().withholdingAllocations().get(0).amount());
        assertMoney("90000",april.getSwissBasis().withholdingAllocations().get(0).rateIncome());
    }
    @Test void thirteenthPaidAfterCantonChangeDoesNotTurn65000Into75000() {
        var source=new SwissSourceTax();var ledger=new PayrollLedgerService(org.mockito.Mockito.mock(com.chrono.chrono.repositories.PayslipRepository.class),new ObjectMapper());
        var entries=new ArrayList<PayrollLedgerService.Entry>();
        for(int month=1;month<=12;month++) {
            var t=terms(month<=8?"TI":"GE","ANNUAL"); LocalDate a=LocalDate.of(2026,month,1),b=a.withDayOfMonth(a.lengthOfMonth());
            var h=history(entries);var g=gross("5000").withThirteenth(ledger.thirteenth(t,a,b,gross("5000"),h));
            var result=SwissPayrollSettlement.calculate(source,coverage(),t,a,b,g,h);
            assertTrue(result.getSwissBasis().annualRateIncome().subtract(n("65000")).abs().compareTo(n("0.000001"))<0);
            entries.add(entry(month,t,g,result));
        }
        assertMoney("5000",entries.get(11).calculation().gross().thirteenthAmount());
    }
    @Test void sourceTaxStatusChangeClosesTheAnnualAccountButKeepsOrdinarySalaryUntaxedAtSource() {
        var source=new SwissSourceTax();var vd=terms("VD","NONE");
        var jan=SwissPayrollSettlement.calculate(source,coverage(),vd,january,januaryEnd,gross("5000"),empty());
        var ordinary=PayrollTestTerms.change(vd,Map.of("taxMode","CH_ORDINARY"));
        var feb=SwissPayrollSettlement.calculate(source,coverage(),ordinary,january.plusMonths(1),LocalDate.of(2026,2,28),gross("5000"),history(List.of(entry(1,vd,gross("5000"),jan))));
        assertFalse(feb.getEmployee().containsKey("Withholding tax"));assertNull(feb.getSwissBasis().annualRateIncome());
        assertMoney("0",feb.getSwissBasis().withholdingAllocations().get(0).amount());
    }
    @Test void newKtgContractUsesOnlyItsOwnGrossAndDaysAndRetainsTheStatutoryAlvYear() {
        var t=PayrollTestTerms.change(terms("ZH","NONE"),Map.of("amount","10000"));
        var first=coverage();first.insurance().setKtgAnnualSalaryCap(n("60000"));
        var jan=SwissPayrollSettlement.calculate(new SwissSourceTax(),first,t,january,januaryEnd,gross("10000"),empty());
        var second=coverage();second.insurance().setId(44L);second.insurance().setKtgAnnualSalaryCap(n("120000"));
        second.insurance().setKtgPoolKey("New-policy");second.insurance().setKtgPoolFrom(LocalDate.of(2026,2,1));second.insurance().setKtgPoolThrough(LocalDate.of(2026,12,31));
        var feb=SwissPayrollSettlement.calculate(new SwissSourceTax(),second,t,LocalDate.of(2026,2,1),LocalDate.of(2026,2,28),gross("10000"),history(List.of(entry(1,t,gross("10000"),jan))));
        assertMoney("5000",jan.getSwissBasis().ktgBase());assertMoney("10000",feb.getSwissBasis().ktgBase());
        assertEquals("New-policy",feb.getSwissBasis().ktgPool().key());assertMoney("10000",feb.getSwissBasis().alvUvgBase());
    }
    PayrollTerms hourly(String vacationMode,String vacationRate,String holidayRate) {
        return PayrollTestTerms.change(terms("ZH","MONTHLY"),Map.of("payType","HOURLY","amount","30","contract",Map.of(
                "salaryProration","THIRTY_DAYS","pensionProration","FULL","thirteenthFrequency","MONTHLY","hourlyAllowance",Map.of(
                "vacationMode",vacationMode,"vacationRate",vacationRate,"holidayRate",holidayRate,"allowanceBase","BASE_ONLY",
                "cashVacationPermitted",true,"allowancesInThirteenth",true,"paidAbsencesInThirteenth",true))));
    }
    @Test void hourlyPaySeparatesWorkedWagesAndAllowancesAndAccruesThirteenthOnlyOnTheAgreedBasis() {
        var t=hourly("CASH_ALLOWANCE","0.0833","0.0039");PayrollTermsService.validate(t);
        var result=PayrollGrossCalculator.calculate(t,january,januaryEnd,List.of(PayrollGrossCalculatorTest.day(january,480)),null,false,null);
        assertMoney("240",result.base());assertMoney("20",result.extraEarnings().get("Vacation allowance"));assertMoney("0.95",result.extraEarnings().get("Holiday allowance"));
        assertMoney("260.95",result.total());assertMoney("260.95",result.eligibleThirteenthBase());assertEquals(0,result.paidOvertimeMinutes());
    }
    @Test void hourlyEmployerAbsencePayUsesRecordedMinutesAndAverageRateWithoutCreatingOvertime() {
        var t=hourly("PAID_ABSENCE","0","0");
        var data=new PayrollPeriodData(List.of(new PayrollPeriodData.AbsencePayment(january,"VACATION",240,n("32.50"),BigDecimal.ONE),
                new PayrollPeriodData.AbsencePayment(january.plusDays(1),"EMPLOYER_SICK_PAY",480,n("30"),n("0.8"))),null);
        var result=PayrollGrossCalculator.calculate(t,january,januaryEnd,List.of(PayrollGrossCalculatorTest.day(january,240)),null,false,null,data);
        assertMoney("120",result.base());assertMoney("130",result.extraEarnings().get("Paid vacation"));assertMoney("192",result.extraEarnings().get("Employer sick pay"));
        assertMoney("442",result.total());assertMoney("442",result.eligibleThirteenthBase());assertMoney("0",result.premium());
    }
    @Test void vacationCashAllowanceCannotBePaidAgainAsVacationAbsence() {
        var t=hourly("CASH_ALLOWANCE","0.0833","0");
        var data=new PayrollPeriodData(List.of(new PayrollPeriodData.AbsencePayment(january,"VACATION",480,n("30"),BigDecimal.ONE)),null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->PayrollGrossCalculator.calculate(t,january,januaryEnd,List.of(),null,false,null,data));
    }
    @Test void absenceComponentsRoundOnceAfterSummingExactWageMinutes() {
        var t=PayrollTestTerms.change(hourly("PAID_ABSENCE","0","0"),Map.of("amount","0.50"));
        var lines=java.util.stream.IntStream.range(0,3).mapToObj(i->new PayrollPeriodData.AbsencePayment(january.plusDays(i),"VACATION",1,n("0.50"),BigDecimal.ONE)).toList();
        var result=PayrollGrossCalculator.calculate(t,january,januaryEnd,List.of(),null,false,null,new PayrollPeriodData(lines,null));
        assertMoney("0.05",result.extraEarnings().get("Paid vacation")); // 3 * 0.50 /60 = 0.025 exactly
    }
    @Test void exactAnnualNormalizationCannotFallBelowAGenevaWholeFrancBoundary() {
        var t=PayrollTestTerms.change(terms("GE","ANNUAL"),Map.of("contract",Map.of("exitDate","2026-08-31","salaryProration","THIRTY_DAYS","pensionProration","FULL","thirteenthFrequency","ANNUAL")));
        var r=SwissPayrollSettlement.calculate(new SwissSourceTax(),coverage(),t,january,januaryEnd,gross("5000"),empty());
        assertMoney("65000",r.getSwissBasis().annualRateIncome());
    }
    @Test void concurrentGermanCeilingsCapEachJobBeforeProportionalAllocation() {
        assertMoney("4225",GermanPayrollTax.concurrentShare(n("10000"),n("20000"),n("8450"),30));
        assertMoney("3520.83",GermanPayrollTax.concurrentShare(n("5000"),n("7000"),n("8450"),30));
        assertMoney("2000",GermanPayrollTax.concurrentShare(n("2000"),n("3000"),n("8450"),30));
        assertMoney("2112.50",GermanPayrollTax.concurrentShare(n("5000"),n("7000"),n("8450"),15));
    }
    @Test void annualGermanTaxMatchesIndependentBmfProgramTests() throws Exception {
        var json=new ObjectMapper();var fixture=json.readTree(getClass().getResourceAsStream("/payroll/bmf-2026-year-end-reference.json"));
        for(var sample:fixture.path("cases")) {
            var in=sample.path("inputs");BigDecimal annual=in.path("RE4").decimalValue().movePointLeft(2);
            Map<String,Object> changes=new HashMap<>();changes.put("entryDate","2026-01-01");changes.put("taxClass",in.path("STKL").asInt());
            changes.put("childAllowance",in.path("ZKF").asText());changes.put("churchRate",in.path("R").asInt()==1?"0.09":"0");
            changes.put("parent",in.path("PVZ").asInt()==0);changes.put("childrenUnder25",in.path("PVA").asInt()==0?0:in.path("PVA").asInt()+1);
            var t=PayrollTestTerms.change(PayrollTestTerms.german(),changes);List<PayrollLedgerService.Entry> entries=new ArrayList<>();
            for(int month=1;month<=12;month++) {
                BigDecimal amount=month==12?annual.subtract(n("5000").multiply(n("11"))):n("5000");
                var g=gross(amount.toPlainString());LocalDate a=LocalDate.of(2026,month,1),b=a.withDayOfMonth(a.lengthOfMonth());
                var r=GermanPayrollTax.calculateRun(t,g,a,b,history(entries));entries.add(entry(month,t,g,r));
            }
            var result=GermanYearEndTax.calculate(entries.stream().map(PayrollLedgerService.Entry::calculation).toList());
            assertMoney(new BigDecimal(sample.path("outputs").path("LSTLZZ").asText()).movePointLeft(2).toPlainString(),result.get("Income tax"));
            assertMoney(new BigDecimal(sample.path("outputs").path("SOLZLZZ").asText()).movePointLeft(2).toPlainString(),result.get("Solidarity surcharge"));
            assertMoney(new BigDecimal(sample.path("outputs").path("BK").asText()).movePointLeft(2).multiply(t.churchRate()).setScale(2,RoundingMode.HALF_UP).toPlainString(),result.get("Church tax"));
        }
    }
    @Test void yearAssignmentKeepsNewUnpaidRemunerationAndMissedDeadlinesBlocked() {
        var a=new PayrollYearAssignment("Original wage bank evidence",true,true,true);
        assertDoesNotThrow(()->PayrollCorrectionService.validateCorrectionDate(january,LocalDate.of(2027,1,31),a));
        assertThrows(IllegalStateException.class,()->PayrollCorrectionService.validateCorrectionDate(january,LocalDate.of(2027,4,1),a));
        assertThrows(IllegalStateException.class,()->PayrollCorrectionService.validateCorrectionDate(january,LocalDate.of(2027,1,31),new PayrollYearAssignment("New unpaid wage",false,true,true)));
        assertThrows(IllegalStateException.class,()->PayrollCorrectionService.validateCorrectionDate(january,LocalDate.of(2027,1,31)));
    }
    static void assertMoney(String expected,BigDecimal actual){assertNotNull(actual);assertEquals(0,n(expected).compareTo(actual),()->"Expected "+expected+" but got "+actual);}
}
