package com.chrono.chrono.services;
import com.chrono.chrono.services.payroll.*;
import com.chrono.chrono.entities.*;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.chrono.chrono.services.PayrollExtendedCalculationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PayrollInsuranceAllocationTest {
    LocalDate jan=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31),middle=LocalDate.of(2026,1,16);
    void money(String expected,BigDecimal value){assertEquals(0,n(expected).compareTo(value));}
    @Test void publishedGkvExample12UsesTheTenDayFrameRatherThanInventedDailyEarnings() {
        // Joint principles §22, example 12: original 2014 limits and independently published results.
        var data=new PayrollPeriodData.GermanConcurrentIncome(null,null,null,null,true,List.of(new PayrollPeriodData.GermanEmployment("B",LocalDate.of(2026,5,6),LocalDate.of(2026,5,11),n("850"),n("850"))));
        var result=GermanConcurrentSettlement.calculate(n("700"),LocalDate.of(2026,5,2),LocalDate.of(2026,5,6),data,n("6050"),n("4125"));
        money("620.97",result.health());money("700",result.pension());assertEquals(10,result.frameDays());
    }
    @Test void fullMonthEmploymentRetainsFullMonthlyCeilingWhenAnotherJobStartsOnThe26th() {
        var data=new PayrollPeriodData.GermanConcurrentIncome(null,null,null,null,true,List.of(new PayrollPeriodData.GermanEmployment("B",jan.withDayOfMonth(26),end,n("360"),n("360"))));
        var result=GermanConcurrentSettlement.calculate(n("2500"),jan,end,data);money("2500",result.health());assertEquals(30,result.frameDays());
    }
    @Test void nonoverlappingOtherEmploymentDoesNotConsumeTheOwnCeiling() {
        var data=new PayrollPeriodData.GermanConcurrentIncome(null,null,null,null,true,List.of(new PayrollPeriodData.GermanEmployment("B",middle,end,n("20000"),n("20000"))));
        var result=GermanConcurrentSettlement.calculate(n("1000"),jan,jan.withDayOfMonth(5),data);money("968.75",result.health());assertEquals(5,result.frameDays());
    }
    @Test void moreThanOneOtherEmployerIsCappedIndividuallyBeforeTheRatio() {
        var data=new PayrollPeriodData.GermanConcurrentIncome(null,null,null,null,true,List.of(
                new PayrollPeriodData.GermanEmployment("B",jan,end,n("20000"),n("20000")),new PayrollPeriodData.GermanEmployment("C",jan,end,n("20000"),n("20000"))));
        var result=GermanConcurrentSettlement.calculate(n("20000"),jan,end,data);money("2816.67",result.pension());money("1937.50",result.health());
    }
    PayrollConfigurationVersion insurance(long id,String key,LocalDate from,LocalDate through,String cap,String nbu,String ktg) {
        var i=coverage().insurance();i.setId(id);i.setUvgNbuRate(n(nbu));i.setKtgRateEmployee(n(ktg));i.setKtgAnnualSalaryCap(n(cap));
        if(key!=null){i.setUvgPoolKey(key);i.setUvgPoolFrom(from);i.setUvgPoolThrough(through);i.setKtgPoolKey(key);i.setKtgPoolFrom(from);i.setKtgPoolThrough(through);}return i;
    }
    PayrollConfigurationVersion pension(long id,String employee){var p=coverage().pension();p.setId(id);p.setBvgEmployee(n(employee));p.setBvgEmployer(BigDecimal.ZERO);return p;}
    PayrollPeriodData inputs(String first,String second,String pensionFirst,String pensionSecond,LocalDate change){return new PayrollPeriodData(List.of(),null,null,List.of(
            new PayrollPeriodData.SwissInsuranceAllocation(jan,change.minusDays(1),n(first),n(pensionFirst),BigDecimal.ZERO),
            new PayrollPeriodData.SwissInsuranceAllocation(change,end,n(second),n(pensionSecond),BigDecimal.ZERO)));}
    PayrollConfigurationService.Coverage split(PayrollConfigurationVersion first,PayrollConfigurationVersion second,PayrollConfigurationVersion p1,PayrollConfigurationVersion p2,LocalDate change){return new PayrollConfigurationService.Coverage(first,p1,true,!p1.getId().equals(p2.getId()),List.of(
            new PayrollConfigurationService.CoverageSlice(jan,change.minusDays(1),first,p1),new PayrollConfigurationService.CoverageSlice(change,end,second,p2)));}
    PayrollTerms ordinary(){return PayrollTestTerms.change(terms("ZH","NONE"),Map.of("taxMode","CH_ORDINARY"));}
    @Test void midmonthInsurerSwitchSeparatesUvgAndKtgWhileAlvRemainsContinuous() {
        var first=insurance(20,"OLD",jan,middle.minusDays(1),"120000","0.02","0.01");var second=insurance(21,"NEW",middle,end.withMonth(12),"240000","0.03","0.02");
        var r=SwissInsuranceSettlement.calculate(new SwissSourceTax(),split(first,second,pension(30,"100"),pension(31,"200"),middle),ordinary(),jan,end,gross("30000"),empty(),inputs("15000","15000","100","200",middle));
        money("12350",r.getSwissBasis().alvUvgBase());money("15000",r.getSwissBasis().ktgBase());
        money("6175",r.getSwissBasis().insuranceSlices().get(0).uvgBase());money("6175",r.getSwissBasis().insuranceSlices().get(1).uvgBase());
        assertEquals(308.75,r.getEmployee().get("NBU"));assertEquals(250.0,r.getEmployee().get("KTG"));assertEquals(300.0,r.getEmployee().get("BVG"));
    }
    @Test void unusedCeilingIsNotTransferredToANewInsurer() {
        var first=insurance(20,"OLD",jan,middle.minusDays(1),"0","0.02","0");var second=insurance(21,"NEW",middle,end.withMonth(12),"0","0.03","0");var p=pension(30,"0");
        var r=SwissInsuranceSettlement.calculate(new SwissSourceTax(),split(first,second,p,p,middle),ordinary(),jan,end,gross("30000"),empty(),inputs("0","30000","0","0",middle));
        money("12350",r.getSwissBasis().alvUvgBase());money("6175",r.getSwissBasis().insuranceSlices().get(1).uvgBase());assertEquals(185.25,r.getEmployee().get("NBU"));
    }
    @Test void rateChangeOnThe31stDoesNotCreateAnExtraDayInTheSameInsurancePool() {
        var first=insurance(20,null,jan,end,"0","0.02","0");var second=insurance(21,null,jan,end,"0","0.03","0");var p=pension(30,"0");var change=end;
        var r=SwissInsuranceSettlement.calculate(new SwissSourceTax(),split(first,second,p,p,change),ordinary(),jan,end,gross("30600"),empty(),inputs("30000","600","0","0",change));
        money("0",r.getSwissBasis().insuranceSlices().get(1).uvgBase());assertEquals(247.0,r.getEmployee().get("NBU"));
    }
    @Test void incompleteOrInventedGrossAndPensionAllocationsAreRejected() {
        var first=insurance(20,null,jan,end,"0","0","0");var second=insurance(21,null,jan,end,"0","0","0");var p=pension(30,"100");var coverage=split(first,second,p,p,middle);
        assertThrows(IllegalStateException.class,()->SwissInsuranceSettlement.calculate(new SwissSourceTax(),coverage,ordinary(),jan,end,gross("5000"),empty(),null));
        assertThrows(IllegalStateException.class,()->SwissInsuranceSettlement.calculate(new SwissSourceTax(),coverage,ordinary(),jan,end,gross("5000"),empty(),inputs("2400","2500","50","50",middle)));
        assertThrows(IllegalStateException.class,()->SwissInsuranceSettlement.calculate(new SwissSourceTax(),coverage,ordinary(),jan,end,gross("5000"),empty(),inputs("2500","2500","100","100",middle)));
    }
    @Test void regularAndPostExitPaymentTaxMatchesFourIndependentBmfProgramTestFixtures() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();var fixture=json.readTree(getClass().getResourceAsStream("/payroll/bmf-2026-payments-reference.json")).path("cases");assertEquals(4,fixture.size());
        for(int index=0;index<fixture.size();index++) {
            var contract=new HashMap<String,Object>();contract.put("salaryProration","THIRTY_DAYS");contract.put("pensionProration","FULL");contract.put("thirteenthFrequency","NONE");contract.put("germanPreviousEmploymentGross","0");
            if(index>=2)contract.put("exitDate",index==2?"2026-01-31":"2026-01-15");
            var t=PayrollTestTerms.change(PayrollTestTerms.german(),Map.of("entryDate","2026-01-01","contract",contract));
            LocalDate start=index==2?LocalDate.of(2026,3,1):index==3?jan.withDayOfMonth(16):jan;var through=start.withDayOfMonth(start.lengthOfMonth());
            BigDecimal oneOff=index==0?BigDecimal.ZERO:index==1?n("1000"):n("500");
            var g=index==0?gross("5500"):index==1?gross("5000").withThirteenth(oneOff):gross("0").withThirteenth(oneOff);
            var h=empty();if(index>=2) {
                var past=new PayrollCalculationService.Calculation(1L,t,jan,index==2?end:jan.withDayOfMonth(15),List.of(),gross(index==2?"5000":"2500"),Map.of(),Map.of(),"Independent fixture context");
                h=history(List.of(new PayrollLedgerService.Entry(new Payslip(),new Payslip(),past)));
            }
            var actual=GermanPayrollTax.taxes(t,g,start,through,h,oneOff);var expected=fixture.get(index).path("outputs");
            assertEquals(new BigDecimal(expected.path("LSTLZZ").asText()).movePointLeft(2).doubleValue(),actual.get("Income tax"),"BMF regular "+index);
            assertEquals(new BigDecimal(expected.path("STS").asText()).movePointLeft(2).doubleValue(),actual.getOrDefault("Income tax · thirteenth",0.0),"BMF one-off "+index);
            assertEquals(new BigDecimal(expected.path("SOLZS").asText()).movePointLeft(2).doubleValue(),actual.getOrDefault("Solidarity surcharge · thirteenth",0.0),"BMF solidarity "+index);
        }
    }
}
