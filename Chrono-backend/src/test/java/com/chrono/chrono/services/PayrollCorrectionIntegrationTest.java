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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import({PayrollService.class,PayrollCorrectionService.class,PayrollCalculationService.class,PayrollLedgerService.class,
        PayrollApprovalGuard.class,TaxCalculationService.class,SwissSourceTax.class,PayrollTermsIntegrationTest.Json.class})
class PayrollCorrectionIntegrationTest {
    @MockitoBean PayrollTermsService terms;
    @MockitoBean PayrollConfigurationService configuration;
    @MockitoBean TimeTrackingService tracking;
    @MockitoBean EmploymentModelHistoryService employment;
    @MockitoBean EmailService mail;
    @MockitoBean PdfService pdf;
    @MockitoBean AccountingService accounting;
    @Autowired PayrollService payroll;
    @Autowired PayrollCorrectionService corrections;
    @Autowired PayrollLedgerService ledger;
    @Autowired PayslipRepository slips;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    User user;
    Map<Integer,PayrollTerms> rules;
    @BeforeEach void setup() {
        var company=companies.saveAndFlush(new Company("Correction fixture"));
        user=new User(); user.setUsername("correction-"+company.getId()); user.setPersonnelNumber("fixture-"+company.getId()); user.setPassword("test-only"); user.setCountry("CH"); user.setCompany(company);
        user.setIsHourly(false); user.setDailyWorkHours(8.0); user.setExpectedWorkDays(5); user.setTrackingBalanceInMinutes(0); user=users.saveAndFlush(user);
        rules=new HashMap<>(); for(int month=1;month<=12;month++) rules.put(month,PayrollExtendedCalculationTest.terms("VD","ANNUAL"));
        when(terms.resolve(any(),any(),any())).thenAnswer(i->new PayrollTermsService.Resolved(1L,rules.get(((LocalDate)i.getArgument(1)).getMonthValue())));
        when(configuration.resolve(any(),any(),any())).thenReturn(PayrollExtendedCalculationTest.coverage());
        when(employment.resolveUserSnapshotsForRange(any(),any(),any())).thenReturn(Map.of(LocalDate.of(2026,1,1),user));
        when(tracking.getUserPeriodSummary(any(),any(),any())).thenAnswer(i->new TimePeriodSummaryDTO("fixture",i.getArgument(1),i.getArgument(2),0,0,0,0,0,List.of()));
        when(pdf.generatePayslipPdf(any())).thenAnswer(i->"fixture-"+((Payslip)i.getArgument(0)).getId()+".pdf");
    }
    Payslip approve(int month) {
        var start=LocalDate.of(2026,month,1); var slip=payroll.generatePayslip(user.getId(),start,start.withDayOfMonth(start.lengthOfMonth()),null);
        payroll.approvePayslip(slip.getId(),"fixture"); return slip;
    }
    PayrollCorrectionService.Request request() { return new PayrollCorrectionService.Request("Vertrag rückwirkend berichtigt",LocalDate.of(2026,10,16),null); }
    @Test void chainRecomputesAllFollowingMonthsAndPreservesOriginalsAndApprovals() throws Exception {
        var originals=List.of(approve(1),approve(2),approve(3));
        var before=originals.stream().map(this::fingerprint).toList();
        rules.put(1,PayrollTestTerms.change(rules.get(1),Map.of("amount","6000")));
        var batch=corrections.generate(originals.get(0).getId(),request(),"test-admin");
        assertEquals(3,batch.size()); assertEquals(before,originals.stream().map(this::fingerprint).toList());
        assertTrue(batch.stream().noneMatch(Payslip::isApproved));
        assertThrows(IllegalStateException.class,()->payroll.approvePayslip(batch.get(0).getId(),"incomplete"));
        payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"reviewed");
        assertTrue(batch.stream().allMatch(Payslip::isApproved));
        assertEquals(before,originals.stream().map(this::fingerprint).toList());
        for(int i=0;i<3;i++) {
            var delta=batch.get(i); var original=originals.get(i); var effective=ledger.read(delta);
            assertEquals(effective.gross().total().doubleValue(), original.getGrossSalary()+delta.getGrossSalary(),0.0000001);
            double deductions=new TaxCalculationService.Result(effective.employee(),effective.employer()).getEmployeeTotal();
            assertEquals(effective.gross().total().doubleValue()-deductions, original.getNetSalary()+delta.getNetSalary(),0.0000001);
            assertEquals(original.getId(),delta.getCorrectionOfId());
        }
        // The following month's annual correction changes even though its monthly salary is unchanged.
        assertEquals(0.0,batch.get(1).getGrossSalary()); assertNotEquals(0.0,batch.get(1).getDeductions());
        payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"retry");
        verify(accounting,times(6)).recordPayrollPosting(any());
    }
    @Test void staleOrCompetingCorrectionCannotOverwriteTheApprovedRevision() {
        var original=approve(1); var before=fingerprint(original);
        rules.put(1,PayrollTestTerms.change(rules.get(1),Map.of("amount","4000")));
        var first=corrections.generate(original.getId(),request(),"test-admin");
        var competing=corrections.generate(original.getId(),request(),"test-admin");
        assertTrue(first.get(0).getNetSalary()<0); // valid refund/receivable, not a corrupted original
        payroll.approveBatch(first.stream().map(Payslip::getId).toList(),"reviewed");
        assertThrows(IllegalStateException.class,()->payroll.approveBatch(competing.stream().map(Payslip::getId).toList(),"stale"));
        assertEquals(before,fingerprint(original)); assertFalse(competing.get(0).isApproved());
        assertThrows(IllegalStateException.class,()->payroll.deletePayslip(first.get(0).getId()));
        assertThrows(IllegalStateException.class,()->corrections.discard(first.get(0).getId()));
    }
    @Test void changedConfigurationOrNewFollowingMonthRequiresNewCorrectionRun() {
        var original=approve(1); var batch=corrections.generate(original.getId(),request(),"test-admin");
        approve(2);
        assertThrows(IllegalStateException.class,()->payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"stale history"));
        corrections.discard(batch.get(0).getId());
        assertTrue(slips.findById(original.getId()).orElseThrow().isApproved()); assertFalse(slips.existsById(batch.get(0).getId()));
        var fresh=corrections.generate(original.getId(),request(),"test-admin");
        rules.put(2,PayrollTestTerms.change(rules.get(2),Map.of("amount","5500")));
        assertThrows(IllegalStateException.class,()->payroll.approveBatch(fresh.stream().map(Payslip::getId).toList(),"changed terms"));
        assertTrue(fresh.stream().noneMatch(Payslip::isApproved));
    }
    @Test void legacyWithoutSnapshotCanBeAdoptedByExplicitDeltaButNeverRewritten() {
        var old=new Payslip(); old.setUser(user); old.setPeriodStart(LocalDate.of(2026,1,1)); old.setPeriodEnd(LocalDate.of(2026,1,31)); old.setCurrency("CHF");
        old.setGrossSalary(5000.01);old.setDeductions(1000.0);old.setNetSalary(4000.01);old.setEmployerContributions(600.0);old.setApproved(true);old.setPdfPath("original.pdf");
        old=slips.saveAndFlush(old); var before=fingerprint(old);
        var batch=corrections.generate(old.getId(),request(),"test-admin"); payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"reviewed legacy");
        assertEquals(before,fingerprint(old));assertNull(old.getCalculationSnapshot());assertEquals("original.pdf",old.getPdfPath());
        assertEquals(5000.0,old.getGrossSalary()+batch.get(0).getGrossSalary(),0.000001);
        assertTrue(batch.get(0).getEarnings().stream().anyMatch(c->c.getType().equals("Legacy earnings difference")));
        assertDoesNotThrow(()->approve(2));
    }
    @Test void missingJanuaryBlocksAnnualFebruaryInsteadOfInventingOpeningBalances() {
        assertThrows(IllegalStateException.class,()->approve(2)); assertEquals(0,slips.count());
    }
    @Test void crossYearCorrectionIsBlockedAtCreationAndAgainAtApproval() {
        var original=approve(1);
        assertThrows(IllegalStateException.class,()->corrections.generate(original.getId(),new PayrollCorrectionService.Request("Vorjahr",LocalDate.of(2027,1,1),null),"fixture"));
        assertEquals(1,slips.count());
        var batch=corrections.generate(original.getId(),request(),"fixture");
        batch.get(0).setPayoutDate(LocalDate.of(2027,1,1)); slips.saveAndFlush(batch.get(0));
        assertThrows(IllegalStateException.class,()->payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"invalid year"));
        assertFalse(batch.get(0).isApproved());
    }
    @Test void reversingPaidOvertimeCreditsMinutesWithoutChangingOriginalPayout() {
        user.setTrackingBalanceInMinutes(120); users.saveAndFlush(user);
        when(tracking.getPayrollOvertimeBalance(any(),any())).thenReturn(120);
        var original=payroll.generatePayslip(user.getId(),LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),null,1.5,true);
        payroll.approvePayslip(original.getId(),"paid overtime"); var before=fingerprint(original);
        user.setTrackingBalanceInMinutes(30); users.saveAndFlush(user);
        when(tracking.getPayrollOvertimeBalance(any(),any())).thenReturn(30);
        var batch=corrections.generate(original.getId(),new PayrollCorrectionService.Request("Überstunden bleiben im Zeitkonto",LocalDate.of(2026,10,16),0.0),"fixture");
        assertEquals(-1.5,batch.get(0).getOvertimeHours());
        assertEquals(0,ledger.read(batch.get(0)).gross().paidOvertimeMinutes());
        payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"reviewed");
        assertEquals(before,fingerprint(original));
        assertEquals(0.0,slips.findByUserAndApproved(user,true).stream().filter(Payslip::isPayoutOvertime).mapToDouble(Payslip::getOvertimeHours).sum());
    }
    @Test void salaryCorrectionRetainsPreviouslyPaidMinutesDespiteLaterTimeAccountDebt() {
        user.setTrackingBalanceInMinutes(120); users.saveAndFlush(user);
        when(tracking.getPayrollOvertimeBalance(any(),any())).thenReturn(120);
        var original=payroll.generatePayslip(user.getId(),LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),null,1.5,true);
        payroll.approvePayslip(original.getId(),"paid overtime"); var before=fingerprint(original);
        user.setTrackingBalanceInMinutes(-600); users.saveAndFlush(user);
        when(tracking.getPayrollOvertimeBalance(any(),any())).thenReturn(-600);
        rules.put(1,PayrollTestTerms.change(rules.get(1),Map.of("amount","6000")));
        var batch=corrections.generate(original.getId(),request(),"fixture");
        assertEquals(0.0,batch.get(0).getOvertimeHours()); assertFalse(batch.get(0).isPayoutOvertime());
        assertEquals(90,ledger.read(batch.get(0)).gross().paidOvertimeMinutes());
        payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"reviewed");
        assertEquals(before,fingerprint(original));
        assertThrows(IllegalStateException.class,()->corrections.generate(original.getId(),new PayrollCorrectionService.Request("Mehr auszahlen",LocalDate.of(2026,10,16),2.0),"fixture"));
    }
    private String fingerprint(Payslip p) { return p.getId()+":"+p.getVersion()+":"+p.getGrossSalary()+":"+p.getNetSalary()+":"+p.getDeductions()+":"+p.isApproved()+":"+p.getCalculationSnapshot()+":"+p.getPdfPath(); }
}
