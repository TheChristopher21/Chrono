package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.services.accounting.AccountingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import({PayrollOpeningService.class,PayrollOpeningCorrectionService.class,PayrollPeriodInputService.class,PayrollService.class,PayrollCorrectionService.class,
        PayrollCalculationService.class,PayrollLedgerService.class,PayrollApprovalGuard.class,PayrollTermsService.class,
        PayrollConfigurationService.class,TaxCalculationService.class,SwissSourceTax.class,PayrollTermsIntegrationTest.Json.class})
class PayrollOpeningIntegrationTest {
    @MockitoBean TimeTrackingService tracking;
    @MockitoBean EmploymentModelHistoryService employment;
    @MockitoBean EmailService mail;
    @MockitoBean PdfService pdf;
    @MockitoBean AccountingService accounting;
    @MockitoBean HolidayService holidays;
    @MockitoSpyBean PayrollCalculationService calculations;
    @Autowired PayrollOpeningService openings;
    @Autowired PayrollOpeningCorrectionService openingCorrections;
    @Autowired PayrollPeriodInputService periodInputs;
    @Autowired PayrollService payroll;
    @Autowired PayrollCorrectionService corrections;
    @Autowired PayrollTermsService terms;
    @Autowired PayrollConfigurationService configuration;
    @Autowired PayrollLedgerService ledger;
    @Autowired PayslipRepository slips;
    @Autowired PayrollOpeningRepository versions;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    User user; PayrollTerms contract; Payslip legacy;
    final LocalDate january=LocalDate.of(2026,1,1),february=LocalDate.of(2026,2,1);
    @BeforeEach void setup() {
        var company=companies.saveAndFlush(new Company("Opening integration fixture"));
        user=new User();user.setUsername("opening-"+company.getId());user.setPersonnelNumber("opening-"+company.getId());user.setPassword("test-only");user.setCountry("CH");user.setCompany(company);
        user.setIsHourly(false);user.setDailyWorkHours(8.0);user.setExpectedWorkDays(5);user.setTrackingBalanceInMinutes(360);user=users.saveAndFlush(user);
        contract=PayrollExtendedCalculationTest.terms("VD","ANNUAL");
        terms.add(company.getId(),user.getId(),new PayrollTermsService.Input(user.getId(),january,"Contract",contract));
        BigDecimal zero=BigDecimal.ZERO;
        configuration.addInsurance(company.getId(),user.getId(),new PayrollConfigurationService.InsuranceInput(january,"Policy",zero,zero,zero,zero,zero,zero,zero,zero));
        configuration.addPension(company.getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),january,"Pension statement",zero,zero));
        legacy=new Payslip();legacy.setUser(user);legacy.setPeriodStart(january);legacy.setPeriodEnd(january.withDayOfMonth(31));legacy.setApproved(true);legacy.setLocked(true);
        legacy.setGrossSalary(37.77);legacy.setDeductions(5.0);legacy.setNetSalary(32.77);legacy.setEmployerContributions(0.0);legacy.setCurrency("CHF");legacy.setPdfPath("legacy-unchanged.pdf");
        legacy.setPayoutOvertime(true);legacy.setOvertimeHours(2.0);legacy.setPayoutDate(january.withDayOfMonth(31));legacy.setEarnings(List.of(new PayComponent("Legacy hours only",37.77)));legacy.setDeductionsList(List.of(new PayComponent("Legacy component",5.0)));
        legacy=slips.saveAndFlush(legacy);
        when(tracking.getUserPeriodSummary(any(),any(),any())).thenAnswer(i->new TimePeriodSummaryDTO("fixture",i.getArgument(1),i.getArgument(2),0,0,0,0,0,List.of()));
        when(employment.resolveUserSnapshotsForRange(any(),any(),any())).thenAnswer(i->Map.of(january,i.getArgument(0)));
        when(pdf.generatePayslipPdf(any())).thenAnswer(i->"local-"+((Payslip)i.getArgument(0)).getId()+".pdf");
    }
    PayrollOpeningService.Period period(Map<String,Object> changes) {
        Map<String,Object> p=new LinkedHashMap<>();p.put("start","2026-01-01");p.put("end","2026-01-31");p.put("base","5000");p.put("premium","0");p.put("overtimePayout","0");p.put("thirteenth","0");p.put("thirteenthEligibleBase","5000");p.put("withholding","400");p.put("insuranceDays",30);p.put("alvUvgBase","5000");p.put("ktgBase","5000");p.put("ktgAnnualCap","0");p.putAll(changes);
        return json.convertValue(p,PayrollOpeningService.Period.class);
    }
    PayrollOpeningVersion open(String source,PayrollOpeningService.Period p) {
        return openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),february,source,true,List.of(p)));
    }
    Payslip create(int month,boolean approve) {
        LocalDate a=LocalDate.of(2026,month,1);var p=payroll.generatePayslip(user.getId(),a,a.withDayOfMonth(a.lengthOfMonth()),null);
        if(approve)payroll.approvePayslip(p.getId(),"test");return p;
    }
    @Test void confirmedOpeningReplacesOnlyFinancialYearBasisAndLeavesLegacySlipAndTimeAccountUntouched() {
        String before=fingerprint(legacy);open("Confirmed January payroll",period(Map.of()));
        assertEquals(before,fingerprint(slips.findById(legacy.getId()).orElseThrow()));assertEquals(360,users.findById(user.getId()).orElseThrow().getTrackingBalanceInMinutes());
        verifyNoInteractions(tracking);
        var next=create(2,true);var saved=ledger.read(next);
        assertEquals(5000.0,next.getGrossSalary());assertEquals(558.0,saved.employee().get("Withholding tax"));
        assertEquals(0,new BigDecimal("65000").compareTo(saved.swissBasis().annualRateIncome()));
        assertEquals(1,saved.history().size());assertTrue(saved.history().get(0).originalId()<0);
        assertEquals(before,fingerprint(slips.findById(legacy.getId()).orElseThrow()));
        assertThrows(ResponseStatusException.class,()->open("Attempt to overwrite used opening",period(Map.of())));
    }
    @Test void revisedOpeningMakesAnEarlierDraftStaleWithoutRewritingEitherVersionOrTheDraft() {
        var first=open("First confirmed basis",period(Map.of()));var draft=create(2,false);String before=fingerprint(draft),old=json.valueToTree(first).toString();
        open("Corrected opening before payroll approval",period(Map.of("withholding","300")));
        assertEquals(2,versions.count());assertEquals(old,json.valueToTree(versions.findById(first.getId()).orElseThrow()).toString());
        assertThrows(IllegalStateException.class,()->payroll.approvePayslip(draft.getId(),"stale"));assertEquals(before,fingerprint(draft));assertFalse(draft.isApproved());
    }
    @Test void importedThirteenthEntitlementAndInsuranceBasesContinueThroughYearEndWithoutPayingOldHoursAgain() {
        doReturn(LocalDate.of(2027,1,10)).when(calculations).payrollToday();
        String before=fingerprint(legacy);open("January actuals",period(Map.of()));
        Payslip last=null;for(int month=2;month<=12;month++)last=create(month,true);
        assertEquals(0,new BigDecimal("5000").compareTo(ledger.read(last).gross().thirteenthAmount()));
        assertEquals(10000.0,last.getGrossSalary());assertEquals(before,fingerprint(slips.findById(legacy.getId()).orElseThrow()));
        assertEquals(2.0,slips.findByUserAndApproved(user,true).stream().filter(Payslip::isPayoutOvertime).mapToDouble(Payslip::getOvertimeHours).sum());
    }
    @Test void openingRejectsGapsUnknownAmountsFalseConfirmationAndCrossTenantPerson() {
        assertThrows(RuntimeException.class,()->open("Gap",period(Map.of("start","2026-01-02"))));
        Map<String,Object> unknown=new HashMap<>();unknown.put("withholding",null);
        assertThrows(ResponseStatusException.class,()->open("Unknown tax",period(unknown)));
        assertThrows(ResponseStatusException.class,()->openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),february,"Unconfirmed",false,List.of(period(Map.of())))));
        var other=companies.saveAndFlush(new Company("Other tenant"));
        var error=assertThrows(ResponseStatusException.class,()->openings.add(other.getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),february,"Cross tenant",true,List.of(period(Map.of())))));
        assertEquals(404,error.getStatusCode().value());assertEquals(0,versions.count());
    }
    @Test void cannotCreateASecondFinancialOriginalInsideTheImportedWindow() {
        open("Actuals",period(Map.of()));assertThrows(IllegalStateException.class,()->create(1,false));assertEquals(1,slips.count());
    }
    PayrollOpeningCorrectionService.Result stage(String source,String withholding) {
        return openingCorrections.stage(user.getCompany().getId(),user.getId(),new PayrollOpeningCorrectionService.Input(
                new PayrollOpeningService.Input(user.getId(),february,source,true,List.of(period(Map.of("withholding",withholding)))),
                new PayrollCorrectionService.Request("Verified opening correction",LocalDate.of(2026,4,30),null)),"test");
    }
    @Test void openingCorrectionActivatesOnlyWithTheWholeApprovedBatchAndPreservesEveryOriginal() {
        var v1=open("Original January",period(Map.of()));String oldParameters=v1.getParameters();
        var feb=create(2,true);var march=create(3,true);var april=create(4,false);
        String febBefore=fingerprint(feb),marchBefore=fingerprint(march),legacyBefore=fingerprint(legacy);
        var staged=stage("Reconciled January","300");var v2=staged.opening();
        assertFalse(v2.isActive());assertEquals(v1.getId(),openings.year(user,february).orElseThrow().version().getId());
        assertEquals(2,staged.corrections().size());
        var batch=slips.findByCorrectionBatch(v2.getCorrectionBatch());
        assertEquals(100.0,batch.get(0).getDeductions());
        assertThrows(IllegalStateException.class,()->payroll.approvePayslip(batch.get(0).getId(),"incomplete"));
        payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"test");
        assertEquals(v2.getId(),openings.year(user,february).orElseThrow().version().getId());assertTrue(v2.isActive());
        assertEquals(oldParameters,versions.findById(v1.getId()).orElseThrow().getParameters());
        assertEquals(febBefore,fingerprint(feb));assertEquals(marchBefore,fingerprint(march));assertEquals(legacyBefore,fingerprint(legacy));
        assertThrows(IllegalStateException.class,()->payroll.approvePayslip(april.getId(),"outdated opening"));
        assertEquals(360,users.findById(user.getId()).orElseThrow().getTrackingBalanceInMinutes());
    }
    @Test void competingOpeningCorrectionsCannotOverwriteTheApprovedNewBasis() {
        open("Original January",period(Map.of()));create(2,true);
        var first=stage("First revision","300");var second=stage("Competing revision","200");
        payroll.approveBatch(first.corrections().stream().map(com.chrono.chrono.dto.PayslipDTO::getId).toList(),"first");
        assertThrows(IllegalStateException.class,()->payroll.approveBatch(second.corrections().stream().map(com.chrono.chrono.dto.PayslipDTO::getId).toList(),"stale"));
        assertEquals(first.opening().getId(),openings.year(user,february).orElseThrow().version().getId());assertFalse(second.opening().isActive());
    }
    @Test void discardingAnOpeningCorrectionPreservesItsInputsAndTheActiveBasis() {
        var first=open("Original January",period(Map.of()));create(2,true);var staged=stage("Discarded revision","300");
        String values=staged.opening().getParameters();corrections.discard(staged.corrections().get(0).getId());
        var discarded=versions.findById(staged.opening().getId()).orElseThrow();assertTrue(discarded.isDiscarded());assertFalse(discarded.isActive());assertEquals(values,discarded.getParameters());
        assertEquals(first.getId(),openings.year(user,february).orElseThrow().version().getId());
        assertTrue(slips.findByCorrectionBatch(discarded.getCorrectionBatch()).isEmpty());
    }
    @Test void anOpeningCorrectionNeverAdoptsALaterLegacyHoursOnlySlip() {
        open("Original January",period(Map.of()));var original=create(2,true);
        legacy.setPeriodStart(LocalDate.of(2026,9,1));legacy.setPeriodEnd(LocalDate.of(2026,9,30));slips.flush();
        String before=fingerprint(legacy);var result=stage("Reconciled January","300");
        assertEquals(1,result.corrections().size());assertEquals(original.getId(),result.corrections().get(0).getCorrectionOfId());
        payroll.approveBatch(result.corrections().stream().map(com.chrono.chrono.dto.PayslipDTO::getId).toList(),"test");
        assertEquals(before,fingerprint(legacy));assertNull(legacy.getCalculationSnapshot());assertEquals(2.0,legacy.getOvertimeHours());
    }
    @Test void missingFinancialMonthsRequireExplicitOpeningEvenWhenThereIsNoTaxToDeduct() {
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),january,"Ordinary tax",PayrollTestTerms.change(contract,Map.of("taxMode","CH_ORDINARY"))));
        assertThrows(IllegalStateException.class,()->create(2,false));assertEquals(1,slips.count());
    }
    @Test void openingUsesCumulativeSwissBasesInsteadOfMistakingACatchUpForAnInvalidMonth() {
        var jan=period(Map.of("base","35000","thirteenthEligibleBase","35000","alvUvgBase","12350","ktgBase","35000"));
        var feb=period(Map.of("start","2026-02-01","end","2026-02-28","alvUvgBase","12350"));
        var v=openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),LocalDate.of(2026,3,1),"Verified cumulative insurance adjustment",true,List.of(jan,feb)));
        assertNotNull(v.getId());var history=ledger.before(user,contract,LocalDate.of(2026,3,1),Map.of());assertTrue(history.complete());
        assertEquals(0,new BigDecimal("12350").compareTo(history.entries().get(1).calculation().swissBasis().alvUvgBase()));
    }
    @Test void oldOpeningTaxReconciliationCanBeAttributedToItsClosedCantonPeriod() {
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),february,"Source tax ended",PayrollTestTerms.change(contract,Map.of("taxMode","CH_ORDINARY"))));
        var feb=period(Map.of("start","2026-02-01","end","2026-02-28","withholding","-10","taxAllocations",List.of(Map.of("canton","VD","from","2026-01-01","amount","-10"))));
        openings.add(user.getCompany().getId(),user.getId(),new PayrollOpeningService.Input(user.getId(),LocalDate.of(2026,3,1),"Closed canton tax correction",true,List.of(period(Map.of()),feb)));
        var h=ledger.before(user,PayrollTestTerms.change(contract,Map.of("taxMode","CH_ORDINARY")),LocalDate.of(2026,3,1),Map.of());
        assertEquals("VD",h.entries().get(1).calculation().swissBasis().withholdingAllocations().get(0).canton());
        assertEquals(0,new BigDecimal("-10").compareTo(h.entries().get(1).calculation().swissBasis().withholdingAllocations().get(0).amount()));
    }
    static String fingerprint(Payslip p){return p.getId()+":"+p.getVersion()+":"+p.getGrossSalary()+":"+p.getDeductions()+":"+p.getNetSalary()+":"+p.isApproved()+":"+p.getOvertimeHours()+":"+p.isPayoutOvertime()+":"+p.getCalculationSnapshot()+":"+p.getPdfPath();}
}
