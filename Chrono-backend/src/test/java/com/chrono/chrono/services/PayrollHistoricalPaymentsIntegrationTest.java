package com.chrono.chrono.services;
import com.chrono.chrono.services.payroll.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Import({PayrollHistoricalSettlementService.class,PayrollOpeningService.class})
class PayrollHistoricalPaymentsIntegrationTest extends PayrollPaymentsIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired PayrollOpeningService openings;
    void historicalContract(String country,String canton,String exit) {
        var c=new LinkedHashMap<String,Object>();c.put("salaryProration","THIRTY_DAYS");c.put("pensionProration","FULL");c.put("thirteenthFrequency","NONE");c.put("germanPreviousEmploymentGross","0");if(exit!=null)c.put("exitDate",exit);
        var values=new LinkedHashMap<String,Object>();values.put("entryDate","2025-12-01");values.put("contract",c);
        if(canton!=null){values.put("taxMode","CH_SOURCE");values.put("canton",canton);values.put("tariffCode","A0N");}
        rule=PayrollTestTerms.change("DE".equals(country)?PayrollTestTerms.german():PayrollTestTerms.swiss(),values);
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),LocalDate.of(2025,12,1),"Confirmed 2025 contract",rule));
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),jan,"Confirmed receipt-year contract/status",rule));
        var z=BigDecimal.ZERO;configuration.addInsurance(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.InsuranceInput(LocalDate.of(2025,12,1),"Confirmed 2025 insurance",z,z,z,z,z,z,z,z));
        configuration.addPension(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),LocalDate.of(2025,12,1),"Confirmed 2025 pension",z,z));
        var dec=LocalDate.of(2025,12,1);var b=new PayrollOpeningService.Period(dec,dec.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,z,30,new BigDecimal("5000"),new BigDecimal("5000"),z,new BigDecimal("5000"),new BigDecimal("5000"),null,z,z,null,null,null);
        inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),dec,dec.withDayOfMonth(31),"Confirmed actual original 2025 ledger",true,new PayrollPeriodData(List.of(),null,null,null,null,null,b)));
    }
    PayrollPayment pastPayment(String received,String claim,String kind) {
        return new PayrollPayment("past-2025",kind,LocalDate.of(2025,12,1),LocalDate.of(2025,12,31),LocalDate.parse(received),LocalDate.parse(claim),new BigDecimal("500"),true,BigDecimal.ZERO,BigDecimal.ZERO,true);
    }
    @Override PayrollPayment payment(String reference,String kind,String received,String claim){var p=super.payment(reference,kind,received,claim);return new PayrollPayment(p.reference(),p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),p.gross(),true,null,null,true);}
    @Test void german2025RegularArrearsUse2025SvBut2026ReceiptTaxAndNo2026EmploymentDays() {
        historicalContract("DE",null,"2025-12-31");add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));
        var slip=create(3,true);var c=ledger.read(slip);
        assertEquals(500.0,slip.getGrossSalary());assertTrue(c.remuneration().cashOnly());assertEquals(0,c.germanBasis().insuranceDays());assertEquals(0,c.germanBasis().pensionBase().signum());
        assertEquals(46.5,c.employee().get("Pension insurance · Altperiode 2025-12-01"));assertEquals(6.5,c.employee().get("Unemployment insurance · Altperiode 2025-12-01"));
        assertEquals(500.0,c.remuneration().taxGross().total().doubleValue());assertFalse(c.employee().containsKey("Income tax · Altperiode 2025-12-01"));assertEquals(120,user.getTrackingBalanceInMinutes());
    }
    @Test void swiss2025ArrearsDuringContinuedEmploymentUseTheReceiptYearSvCeiling() {
        historicalContract("CH","ZH",null);create(1,true);create(2,true);add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));
        var c=ledger.read(create(3,true));assertEquals(5500.0,c.remuneration().socialGross().total().doubleValue());assertEquals(5500.0,c.swissBasis().alvUvgBase().doubleValue());
        assertEquals(291.5,c.employee().get("AHV/IV/EO"));assertTrue(c.remuneration().assessment().employee().isEmpty());
    }
    @Test void swissNewRightAfter2025ExitUsesHistoricalInsuranceAndCurrent2026Tax() {
        historicalContract("CH","VD","2025-12-31");add(march,List.of(pastPayment("2026-03-15","2026-03-01","ONE_OFF")));
        var c=ledger.read(create(3,true));assertEquals(0,c.swissBasis().insuranceDays());assertEquals(500.0,c.gross().total().doubleValue());assertEquals(26.5,c.employee().get("AHV/IV/EO · Altperiode 2025-12-01"));
        assertEquals(0,new BigDecimal("60500").compareTo(c.swissBasis().annualRateIncome()));assertTrue(c.employee().get("Withholding tax")>0);
    }
    @Test void changedHistoricalInputsMakeAnExistingReceiptDraftStale() {
        historicalContract("DE",null,"2025-12-31");add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));var draft=create(3,false);
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),LocalDate.of(2025,12,1),"Corrected original 2025 health-fund rate",PayrollTestTerms.change(rule,Map.of("healthAdditionalRate","0.04"))));
        assertThrows(IllegalStateException.class,()->payroll.approvePayslip(draft.getId(),"stale"));assertFalse(slips.findById(draft.getId()).orElseThrow().isApproved());
    }
    void opening(String country,String canton) {
        contract(country,canton,null);var z=BigDecimal.ZERO;
        var p=new PayrollOpeningService.Period(jan,jan.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal("DE".equals(country)?"782.41":"400"),30,new BigDecimal("5000"),new BigDecimal("5000"),z,new BigDecimal("5000"),new BigDecimal("5000"),null,z,z,null,null,null);
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),LocalDate.of(2026,2,1),"Confirmed original opening before new financial payroll",true,List.of(p)));
    }
    @Test void regularPaymentBeforeGermanOpeningCreatesSeparateDeltasAndKeepsTheOpeningAndOriginals() {
        opening("DE",null);var version=openings.year(user,march).orElseThrow().version();String parameters=version.getParameters();var february=create(2,true);String old=fingerprint(february);
        add(march,List.of(payment("before-opening","REGULAR_ARREARS","2026-03-15","2026-01-31")));
        assertThrows(IllegalStateException.class,()->create(3,false));correct(february,"2026-03-15");var receipt=ledger.read(create(3,true));
        assertEquals(5500.0,receipt.gross().total().doubleValue());assertEquals(5000.0,receipt.remuneration().taxGross().total().doubleValue());assertEquals(5000.0,receipt.germanBasis().pensionBase().doubleValue());
        assertEquals(46.5,receipt.employee().get("Pension insurance · Altperiode 2026-01-01"));assertEquals(138.84,receipt.employee().get("Income tax · Altperiode 2026-01-01"));
        assertEquals(old,fingerprint(february));assertEquals(parameters,openings.year(user,march).orElseThrow().version().getParameters());assertEquals(120,user.getTrackingBalanceInMinutes());
    }
    @Test void swissWorkBeforeOpeningIsPaidOnlyOnceAndDoesNotReapplyTheOriginalPension() {
        opening("CH","VD");var february=create(2,true);assertEquals(4263.0,february.getNetSalary());String old=fingerprint(february);
        var p=new PayrollPayment("opening-CH","REGULAR_ARREARS",jan,jan.withDayOfMonth(31),march.withDayOfMonth(15),jan.withDayOfMonth(31),new BigDecimal("500"),true,BigDecimal.ZERO,BigDecimal.ZERO);
        add(march,List.of(p));assertThrows(IllegalStateException.class,()->create(3,false));correct(february,"2026-03-15");assertEquals(0.0,ledger.latest(february).getNetSalary());
        var receipt=ledger.read(create(3,true));assertEquals(5500.0,receipt.gross().total().doubleValue());assertEquals(5000.0,receipt.remuneration().socialGross().total().doubleValue());
        assertEquals(26.5,receipt.employee().get("AHV/IV/EO · Altperiode 2026-01-01"));assertEquals(5.5,receipt.employee().get("ALV · Altperiode 2026-01-01"));assertFalse(receipt.employee().containsKey("BVG · Nachzahlung"));assertEquals(old,fingerprint(february));
    }
    void originalWithholding(String amount) {
        var dec=LocalDate.of(2025,12,1);var ref=inputs.resolve(user,dec,dec.withDayOfMonth(31)).orElseThrow();
        var values=json.convertValue(ref.data().historicalBaseline(),java.util.Map.class);values.put("withholding",amount);
        var baseline=json.convertValue(values,PayrollOpeningService.Period.class);
        inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),dec,dec.withDayOfMonth(31),"Actual original withholding verified against independent 2025 reference",true,new PayrollPeriodData(List.of(),null,null,null,null,null,baseline)));
    }
    @Test void threeWeekRuleAssignsActualJanuaryReceiptToTheOriginal2025TaxPeriod() {
        historicalContract("DE",null,"2025-12-31");originalWithholding("793.33");add(jan,List.of(pastPayment("2026-01-15","2025-12-31","REGULAR_ARREARS")));
        var c=ledger.read(create(1,true));assertEquals(0,c.remuneration().taxGross().total().signum());assertEquals(140.33,c.employee().get("Income tax · Altperiode 2025-12-01"));assertEquals(46.5,c.employee().get("Pension insurance · Altperiode 2025-12-01"));
    }
    @Test void aReceiptAfterJanuary21IsTaxedIn2026DespiteIts2025RegularWageOrigin() {
        historicalContract("DE",null,"2025-12-31");originalWithholding("793.33");add(jan,List.of(pastPayment("2026-01-22","2025-12-31","REGULAR_ARREARS")));
        var c=ledger.read(create(1,true));assertEquals(500.0,c.remuneration().taxGross().total().doubleValue());assertFalse(c.employee().containsKey("Income tax · Altperiode 2025-12-01"));
    }
    @Test void vestedSwissRightsUseTheOriginal2025TariffAndCreditActualOriginalWithholding() {
        historicalContract("CH","ZH","2025-12-31");originalWithholding("287.50");add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));
        var c=ledger.read(create(3,true));assertEquals(0,c.remuneration().taxGross().total().signum());assertEquals(69.45,c.employee().get("Withholding tax · Altperiode 2025-12-01"));assertEquals(398.55,c.gross().total().doubleValue()-c.employee().values().stream().mapToDouble(Double::doubleValue).sum(),0.001);
    }
    @Test void subsequentHistoricalReceiptsConsumeRemaining2025HeadroomWithoutRechargingTheFirstPayment() {
        historicalContract("DE",null,"2025-12-31");add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));var first=create(3,true);String unchanged=fingerprint(first);
        var may=LocalDate.of(2026,5,1);var p=pastPayment("2026-05-15","2025-12-31","REGULAR_ARREARS");
        add(may,List.of(new PayrollPayment("past-2025-second",p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),p.gross(),true,null,null,true)));
        var c=ledger.read(create(5,true));assertEquals(46.5,c.employee().get("Pension insurance · Altperiode 2025-12-01"));assertEquals(1.09,c.employee().get("Health insurance · Altperiode 2025-12-01"));assertEquals(unchanged,fingerprint(first));
    }
    @Test void newlyAgreedRetroactiveGermanRaiseIsRegularSvInTheAgreementMonth() {
        historicalContract("DE",null,null);create(1,true);create(2,true);add(march,List.of(pastPayment("2026-03-15","2026-03-01","RETROACTIVE_RAISE")));
        var c=ledger.read(create(3,true));assertEquals(5500.0,c.germanBasis().pensionBase().doubleValue());assertEquals(511.5,c.employee().get("Pension insurance"));assertFalse(c.employee().containsKey("Pension insurance · Altperiode 2025-12-01"));
    }
    @Test void retroactiveRaiseAgreedAfterExitUsesLastWorkMonthRegularCeilingEvenAfterMarch() {
        historicalContract("DE",null,"2025-12-31");add(LocalDate.of(2026,5,1),List.of(pastPayment("2026-05-15","2026-05-01","RETROACTIVE_RAISE")));
        var c=ledger.read(create(5,true));assertEquals(46.5,c.employee().get("Pension insurance · Altperiode 2025-12-01"));assertEquals(500.0,c.remuneration().taxGross().total().doubleValue());
    }
    @Test void aNewOneOffAfterPreviousYearExitHasSvOnlyWhenReceivedInTheFirstQuarter() {
        historicalContract("DE",null,"2025-12-31");add(march,List.of(pastPayment("2026-03-15","2026-03-01","ONE_OFF")));
        var first=ledger.read(create(3,true));assertEquals(46.5,first.employee().get("Pension insurance · Altperiode 2025-12-01"));
        var may=LocalDate.of(2026,5,1);var p=pastPayment("2026-05-15","2026-05-01","ONE_OFF");add(may,List.of(new PayrollPayment("late-oneoff",p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),p.gross(),true)));
        var second=ledger.read(create(5,true));assertFalse(second.employee().containsKey("Pension insurance · Altperiode 2025-12-01"));assertEquals(0,second.germanBasis().pensionBase().signum());
    }
    @Test void historicalRegularEmployeeSvCannotBeChargedWithoutConfirmedRecoveryRights() {
        historicalContract("DE",null,"2025-12-31");var p=pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS");add(march,List.of(new PayrollPayment(p.reference(),p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),p.gross(),true)));
        assertTrue(assertThrows(IllegalStateException.class,()->create(3,false)).getMessage().contains("28g"));assertEquals(0,slips.count());
    }
    @Test void late2025RegularArrearsRequireTheMarchClauseToUseTheRevisedPriorYearHeadroom() {
        historicalContract("DE",null,null);var bonus=new PayrollPayment("January bonus","ONE_OFF",jan,jan.withDayOfMonth(31),jan.withDayOfMonth(31),jan.withDayOfMonth(31),new BigDecimal("1000"),true);
        add(jan,List.of(bonus));var original=create(1,true);String unchanged=fingerprint(original);assertEquals(512.5,ledger.read(original).germanBasis().priorYear().healthBase().doubleValue());create(2,true);
        add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));assertThrows(IllegalStateException.class,()->create(3,false));correct(original,"2026-03-15");
        assertEquals(12.5,ledger.read(ledger.latest(original)).germanBasis().priorYear().healthBase().doubleValue());var receipt=ledger.read(create(3,true));assertEquals(46.5,receipt.employee().get("Pension insurance · Altperiode 2025-12-01"));assertEquals(unchanged,fingerprint(original));
    }
    @Test void historicalSocialDifferenceUsesRoundedNewAndOriginalContributionsRatherThanRoundingTheDifferenceBase() {
        historicalContract("DE",null,"2025-12-31");var dec=LocalDate.of(2025,12,1);var original=inputs.resolve(user,dec,dec.withDayOfMonth(31)).orElseThrow().data().historicalBaseline();var values=json.convertValue(original,Map.class);
        values.put("base","5000.05");values.put("pensionBase","5000.05");values.put("healthBase","5000.05");var b=json.convertValue(values,PayrollOpeningService.Period.class);
        inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),dec,dec.withDayOfMonth(31),"Original sub-cent contribution boundary",true,new PayrollPeriodData(List.of(),null,null,null,null,null,b)));
        var p=pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS");add(march,List.of(new PayrollPayment(p.reference(),p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),new BigDecimal("0.01"),true,null,null,true)));
        var c=ledger.read(create(3,true));assertEquals(0.01,c.employee().get("Pension insurance · Altperiode 2025-12-01"));
    }
    @Test void aDocumentedHistoricalTaxBalanceMayExceedTheNewCashPaymentWithoutChangingTheOriginalBasis() {
        historicalContract("CH","ZH","2025-12-31");var p=pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS");add(march,List.of(new PayrollPayment(p.reference(),p.kind(),p.earnedFrom(),p.earnedThrough(),p.receivedOn(),p.claimEstablishedOn(),new BigDecimal("0.01"),true,BigDecimal.ZERO,BigDecimal.ZERO)));
        var receipt=create(3,true);assertTrue(receipt.getNetSalary()<0);var a=ledger.read(receipt).remuneration().assessment();assertEquals(0.0,a.originalPeriods().get(0).employee().get("Withholding tax"));assertTrue(a.employee().get("Withholding tax · Altperiode 2025-12-01")>280);
    }
    @Test void aNewCashPayrollStartMayFollowAnExitCoveredOnlyByTheActualOpeningEmploymentMonths() {
        contract("CH","ZH","2026-01-31");var z=BigDecimal.ZERO;var b=new PayrollOpeningService.Period(jan,jan.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal("276"),30,new BigDecimal("5000"),new BigDecimal("5000"),z,null,null,null,z,z,null,null,null);
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),march,"Actual January ledger; no fictitious February employment",true,List.of(b)));
        add(march,List.of(new PayrollPayment("post-opening-exit","REGULAR_ARREARS",jan,jan.withDayOfMonth(31),march.withDayOfMonth(15),jan.withDayOfMonth(31),new BigDecimal("500"),true,z,z)));
        var c=ledger.read(create(3,true));assertTrue(c.remuneration().cashOnly());assertEquals(1,c.history().size());assertEquals(26.5,c.employee().get("AHV/IV/EO · Altperiode 2026-01-01"));
    }
    @Test void originalAndUpdatedInsuranceSectionsBeforeOpeningProduceOnlyTheNewDifferenceAndExplicitPensionExtra() {
        contract("CH","ZH","2026-01-31");var z=BigDecimal.ZERO;var change=jan.withDayOfMonth(16);
        configuration.addInsurance(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.InsuranceInput(change,"Original new insurer after 16 January",z,new BigDecimal("0.02"),z,z,z,z,z,z,null,new PayrollInsurancePool("second insurer",change,LocalDate.of(2026,12,31))));
        configuration.addPension(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),change,"Original new pension agreement",new BigDecimal("200"),z));
        var b=new PayrollOpeningService.Period(jan,jan.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal("276"),30,new BigDecimal("5000"),new BigDecimal("5000"),z,null,null,null,z,z,null,null,null);
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),march,"Actual opening with original mid-month coverage",true,List.of(b)));
        var original=List.of(new PayrollPeriodData.SwissInsuranceAllocation(jan,change.minusDays(1),new BigDecimal("2500"),z,z),new PayrollPeriodData.SwissInsuranceAllocation(change,jan.withDayOfMonth(31),new BigDecimal("2500"),new BigDecimal("100"),z));
        var revised=List.of(original.get(0),new PayrollPeriodData.SwissInsuranceAllocation(change,jan.withDayOfMonth(31),new BigDecimal("3000"),new BigDecimal("100"),z));
        inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),jan,jan.withDayOfMonth(31),"Original and confirmed revised coverage wages",true,new PayrollPeriodData(List.of(),null,null,revised,null,null,null,original)));
        add(march,List.of(new PayrollPayment("old coverage payment","REGULAR_ARREARS",jan,jan.withDayOfMonth(31),march.withDayOfMonth(15),jan.withDayOfMonth(31),new BigDecimal("500"),true,new BigDecimal("50"),new BigDecimal("100"))));
        var c=ledger.read(create(3,true));assertEquals(10.0,c.employee().get("NBU · Altperiode 2026-01-01"));assertEquals(50.0,c.employee().get("BVG · Nachzahlung"));assertEquals(100.0,c.employer().get("BVG · Nachzahlung"));assertFalse(c.employee().containsKey("BVG · Altperiode 2026-01-01"));
    }
    void germanQuarterlyOpening() {
        contract("DE",null,null);
        rule=PayrollTestTerms.change(rule,Map.of("contract",Map.of("salaryProration","THIRTY_DAYS","pensionProration","FULL","thirteenthFrequency","QUARTERLY","germanPreviousEmploymentGross","0")));
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),jan,"Confirmed quarterly thirteenth contract",rule));
        var periods=new ArrayList<PayrollOpeningService.Period>();var entries=new ArrayList<PayrollLedgerService.Entry>();var z=BigDecimal.ZERO;
        for(int month=1;month<=3;month++) {
            var start=LocalDate.of(2026,month,1);var end=start.withDayOfMonth(start.lengthOfMonth());var bonus=month==3?new BigDecimal("1250"):z;
            var gross=new PayrollGrossCalculator.Result(new BigDecimal("5000"),z,z,new BigDecimal("5000"),0).withThirteenth(bonus);
            var taxes=GermanPayrollTax.taxes(rule,gross,start,end,new PayrollLedgerService.History(List.copyOf(entries),List.of(),true),bonus);
            double income=taxes.entrySet().stream().filter(e->e.getKey().startsWith("Income tax")).mapToDouble(Map.Entry::getValue).sum();
            double solidarity=taxes.entrySet().stream().filter(e->e.getKey().startsWith("Solidarity surcharge")).mapToDouble(Map.Entry::getValue).sum();
            double church=taxes.entrySet().stream().filter(e->e.getKey().startsWith("Church tax")).mapToDouble(Map.Entry::getValue).sum();
            periods.add(new PayrollOpeningService.Period(start,end,new BigDecimal("5000"),z,z,bonus,BigDecimal.valueOf(income).setScale(2,java.math.RoundingMode.HALF_UP),30,null,null,null,gross.total(),gross.total(),new BigDecimal("5000"),BigDecimal.valueOf(solidarity).setScale(2,java.math.RoundingMode.HALF_UP),BigDecimal.valueOf(church).setScale(2,java.math.RoundingMode.HALF_UP),null,null,null));
            entries.add(PayrollExtendedCalculationTest.entry(month,rule,gross,new TaxCalculationService.Result(taxes,Map.of())));
        }
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),LocalDate.of(2026,4,1),"Actual January to March payroll, including quarterly thirteenth",true,periods));
    }
    void assertImportedMarchTaxIncludesEarlierRegularArrears(boolean alsoMarch) {
        germanQuarterlyOpening();var april=LocalDate.of(2026,4,1);var rows=new ArrayList<PayrollPayment>();rows.add(payment("January arrears before opening","REGULAR_ARREARS","2026-04-15","2026-01-31"));
        if(alsoMarch)rows.add(new PayrollPayment("March arrears before opening","REGULAR_ARREARS",march,march.withDayOfMonth(31),april.withDayOfMonth(15),march.withDayOfMonth(31),new BigDecimal("500"),true,null,null,true));
        add(april,rows);var c=ledger.read(create(4,true));var original=c.remuneration().assessment().originalPeriods();var target=new ArrayList<PayrollLedgerService.Entry>();
        for(int i=0;i<2;i++) {var old=original.get(i);var g=old.gross();if(i==0)g=new PayrollGrossCalculator.Result(new BigDecimal("5500"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("5500"),0);
            target.add(PayrollExtendedCalculationTest.entry(i+1,rule,g,new TaxCalculationService.Result(old.employee(),Map.of())));
        }
        var oldMarch=original.get(2);var gross=oldMarch.gross();if(alsoMarch)gross=new PayrollGrossCalculator.Result(new BigDecimal("5500"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("6750"),0,new BigDecimal("1250"));
        var expected=GermanPayrollTax.taxes(rule,gross,march,march.withDayOfMonth(31),new PayrollLedgerService.History(target,List.of(),true),new BigDecimal("1250"));
        double expectedIncome=expected.entrySet().stream().filter(e->e.getKey().startsWith("Income tax")).mapToDouble(Map.Entry::getValue).sum();
        double oldIncome=oldMarch.employee().get("Income tax");double difference=c.employee().entrySet().stream().filter(e->e.getKey().startsWith("Income tax")&&e.getKey().endsWith(" · Altperiode 2026-03-01")).mapToDouble(Map.Entry::getValue).sum();
        assertTrue(expectedIncome>oldIncome,"The old quarterly payment must consume the increased JRE4 basis");assertEquals(expectedIncome-oldIncome,difference,0.000001);
    }
    @Test void aRegularArrearInAnEarlierImportedMonthAlsoReassessesALaterImportedQuarterlyThirteenth(){assertImportedMarchTaxIncludesEarlierRegularArrears(false);}
    @Test void anImportedQuarterlyThirteenthUsesRevisedEarlierTaxGrossWhenItsOwnMonthAlsoHasArrears(){assertImportedMarchTaxIncludesEarlierRegularArrears(true);}
    @Test void aSecondQuarterOneOffAfterA2026ExitStillUsesTheImportedLastEmploymentMonthForSv() {
        contract("DE",null,"2026-01-31");var z=BigDecimal.ZERO;var p=new PayrollOpeningService.Period(jan,jan.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal("782.41"),30,null,null,null,new BigDecimal("5000"),new BigDecimal("5000"),null,z,z,null,null,null);
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),march,"Actual last employment month",true,List.of(p)));
        var may=LocalDate.of(2026,5,1);add(may,List.of(payment("May new one-off","ONE_OFF","2026-05-15","2026-05-01")));
        var c=ledger.read(create(5,true));assertEquals(46.5,c.employee().get("Pension insurance · one-off remuneration · Altperiode 2026-01-01"));
        var imported=c.remuneration().assessment().revisedPeriods().get(0);assertEquals(5500.0,imported.germanBasis().pensionBase().doubleValue());assertNull(imported.germanBasis().priorYear());
    }
    @Test void firstAndSecondQuarterOneOffsBeforeOpeningUseIndependent2025And2026Ceilings() {
        historicalContract("DE",null,"2026-01-31");var z=BigDecimal.ZERO;var p=new PayrollOpeningService.Period(jan,jan.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal("782.41"),30,null,null,null,new BigDecimal("5000"),new BigDecimal("5000"),null,z,z,null,null,null);
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),march,"Original January after actual December 2025 employment",true,List.of(p)));
        add(march,List.of(new PayrollPayment("March opening one-off","ONE_OFF",jan,jan.withDayOfMonth(31),march.withDayOfMonth(15),march.withDayOfMonth(1),new BigDecimal("1000"),true)));
        var first=create(3,true);String unchanged=fingerprint(first);var firstCalculation=ledger.read(first);assertEquals(93.0,firstCalculation.employee().get("Pension insurance · SV 2025 · Altperiode 2026-01-01"));
        var firstImported=firstCalculation.remuneration().assessment().revisedPeriods().stream().filter(c->c.periodStart().getYear()==2026).findFirst().orElseThrow();
        assertEquals(5000.0,firstImported.germanBasis().pensionBase().doubleValue());assertEquals(1000.0,firstImported.germanBasis().priorYear().pensionBase().doubleValue());assertEquals(512.5,firstImported.germanBasis().priorYear().healthBase().doubleValue());
        var may=LocalDate.of(2026,5,1);add(may,List.of(payment("May opening one-off","ONE_OFF","2026-05-15","2026-05-01")));assertThrows(IllegalStateException.class,()->create(5,false));correct(first,"2026-05-15");
        var second=ledger.read(create(5,true));assertEquals(46.5,second.employee().get("Pension insurance · one-off remuneration · Altperiode 2026-01-01"));assertFalse(second.employee().containsKey("Pension insurance · SV 2025 · Altperiode 2026-01-01"));
        var latest=second.remuneration().assessment().revisedPeriods().stream().filter(c->c.periodStart().getYear()==2026).findFirst().orElseThrow();assertEquals(5500.0,latest.germanBasis().pensionBase().doubleValue());assertEquals(1000.0,latest.germanBasis().priorYear().pensionBase().doubleValue());assertEquals(unchanged,fingerprint(first));
    }
    @Test void historicalMiniAndMidiJobsRemainBlockedInsteadOfBeingAssessedAtRegularRates() {
        historicalContract("DE",null,"2025-12-31");var lower=PayrollTestTerms.change(rule,Map.of("amount","1500"));terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),LocalDate.of(2025,12,1),"Actual historical midijob status",lower));
        add(march,List.of(pastPayment("2026-03-15","2025-12-31","REGULAR_ARREARS")));assertTrue(assertThrows(IllegalStateException.class,()->create(3,false)).getMessage().contains("Mini-/Midijobs"));
    }
}
