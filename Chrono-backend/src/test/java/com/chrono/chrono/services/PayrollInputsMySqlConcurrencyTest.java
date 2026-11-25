package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in, disposable local schemas only. Proves current reads after an older MySQL RR snapshot. */
@EnabledIfSystemProperty(named="chrono.test.payroll.inputs.mysql.url",matches=".+")
@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PayrollService.class,PayrollCalculationService.class,PayrollLedgerService.class,PayrollApprovalGuard.class,
        PayrollOpeningService.class,PayrollHistoricalSettlementService.class,PayrollPeriodInputService.class,PayrollPaymentLedgerService.class,PayrollTermsService.class,PayrollConfigurationService.class,
        TaxCalculationService.class,SwissSourceTax.class,PayrollTermsIntegrationTest.Json.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PayrollInputsMySqlConcurrencyTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getProperty("chrono.test.payroll.inputs.mysql.url");
        assertNotNull(url);assertTrue(url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/chrono_payroll_concurrency_test_inputs[a-z0-9_]*(\\?.*)?"));
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->System.getProperty("chrono.test.mysql.username","root"));
        properties.add("spring.datasource.password",()->System.getProperty("chrono.test.mysql.password",""));
        properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");properties.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
        properties.add("spring.datasource.hikari.maximum-pool-size",()->"4");
    }
    @MockitoBean TimeTrackingService tracking;
    @MockitoBean EmploymentModelHistoryService employment;
    @MockitoBean HolidayService holidays;
    @MockitoBean EmailService mail;
    @MockitoBean PdfService pdf;
    @MockitoBean AccountingService accounting;
    @Autowired PayrollService payroll;
    @Autowired PayrollOpeningService openings;
    @Autowired PayrollPeriodInputService inputs;
    @Autowired PayrollTermsService terms;
    @Autowired PayrollConfigurationService configuration;
    @Autowired PayslipRepository slips;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired PlatformTransactionManager transactions;
    TransactionTemplate tx;Long userId,companyId;PayrollTerms rule;
    final LocalDate january=LocalDate.of(2026,1,1),januaryEnd=LocalDate.of(2026,1,31),february=LocalDate.of(2026,2,1);
    @BeforeEach void setup() {
        tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status->{
            var c=companies.saveAndFlush(new Company("Current input read fixture"));companyId=c.getId();
            var u=new User();u.setCompany(c);u.setUsername("input-race-"+c.getId());u.setPersonnelNumber("input-race-"+c.getId());u.setPassword("test-only");u.setCountry("CH");u.setIsHourly(false);u.setEntryDate(january);u.setDailyWorkHours(8.0);u.setExpectedWorkDays(5);u.setTrackingBalanceInMinutes(0);userId=users.saveAndFlush(u).getId();
            rule=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("entryDate","2026-01-01"));
            terms.add(companyId,userId,new PayrollTermsService.Input(userId,january,"Original contract",rule));
            insurance("0");configuration.addPension(companyId,userId,new PayrollConfigurationService.PensionInput(userId,january,"Pension",BigDecimal.ZERO,BigDecimal.ZERO));
        });
        when(tracking.getUserPeriodSummary(any(),any(),any())).thenAnswer(i->new TimePeriodSummaryDTO("fixture",i.getArgument(1),i.getArgument(2),480,0,0,0,0,List.of(PayrollGrossCalculatorTest.day(((LocalDate)i.getArgument(1)).plusDays(4),480))));
        when(employment.resolveUserSnapshotsForRange(any(),any(),any())).thenAnswer(i->Map.of(january,i.getArgument(0)));
        when(pdf.generatePayslipPdf(any())).thenReturn("test-only.pdf");
    }
    void insurance(String employeeRate) {configuration.addInsurance(companyId,userId,new PayrollConfigurationService.InsuranceInput(january,"Policy",BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal(employeeRate),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));}
    Long draft(LocalDate start){return tx.execute(status->payroll.generatePayslip(userId,start,start.withDayOfMonth(start.lengthOfMonth()),null).getId());}
    void opening(String source) {openings.add(companyId,userId,new PayrollOpeningService.Input(userId,february,source,true,List.of(new PayrollOpeningService.Period(january,januaryEnd,new BigDecimal("5000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,30,new BigDecimal("5000"),new BigDecimal("5000"),BigDecimal.ZERO,null,null))));}
    @Test void waitingApprovalSeesANewPersonalRuleInsteadOfTheOldConsistentSnapshot() throws Exception {
        Long draft=draft(january);race(draft,()->terms.add(companyId,userId,new PayrollTermsService.Input(userId,january,"Corrected contract",PayrollTestTerms.change(rule,Map.of("amount","6000")))));
    }
    @Test void waitingApprovalSeesANewInsuranceVersion() throws Exception {Long draft=draft(january);race(draft,()->insurance("0.01"));}
    @Test void waitingApprovalSeesALaterReceiptThatChangesThisMonthsSocialAllocation() throws Exception {
        Long draft=draft(january);var march=january.plusMonths(2);
        race(draft,()->inputs.add(companyId,userId,new PayrollPeriodInputService.Input(userId,march,march.withDayOfMonth(31),"Confirmed later receipt",true,
                new PayrollPeriodData(List.of(),null,null,null,List.of(new PayrollPayment("Payment race","REGULAR_ARREARS",january,januaryEnd,march.withDayOfMonth(15),januaryEnd,new BigDecimal("500"),true))))));
    }
    @Test void waitingApprovalSeesTheLatestIndependentOpeningVersion() throws Exception {
        tx.executeWithoutResult(s->opening("Opening one"));Long draft=draft(february);race(draft,()->opening("Opening two"));
    }
    @Test void waitingApprovalSeesChangedHourlyAbsenceInputs() throws Exception {
        tx.executeWithoutResult(s->{
            var u=users.findByIdForUpdate(userId).orElseThrow();u.setIsHourly(true);users.saveAndFlush(u);
            rule=PayrollTestTerms.change(rule,Map.of("payType","HOURLY","amount","30","contract",Map.of("salaryProration","THIRTY_DAYS","pensionProration","FULL","thirteenthFrequency","NONE","hourlyAllowance",Map.of("vacationMode","PAID_ABSENCE","vacationRate","0","holidayRate","0","allowanceBase","BASE_ONLY","cashVacationPermitted",false,"allowancesInThirteenth",false,"paidAbsencesInThirteenth",true))));
            terms.add(companyId,userId,new PayrollTermsService.Input(userId,january,"Hourly contract",rule));
            inputs.add(companyId,userId,new PayrollPeriodInputService.Input(userId,january,januaryEnd,"Reviewed original absence inputs",true,new PayrollPeriodData(List.of(),null)));
        });
        Long draft=draft(january);
        race(draft,()->inputs.add(companyId,userId,new PayrollPeriodInputService.Input(userId,january,januaryEnd,"Corrected holiday pay",true,new PayrollPeriodData(List.of(new PayrollPeriodData.AbsencePayment(january.plusDays(5),"HOLIDAY",240,new BigDecimal("30"),BigDecimal.ONE)),null))));
    }
    void historicalBaseline(String source,String withholding) {
        var dec=LocalDate.of(2025,12,1);var z=BigDecimal.ZERO;var p=new PayrollOpeningService.Period(dec,dec.withDayOfMonth(31),new BigDecimal("5000"),z,z,z,new BigDecimal(withholding),30,new BigDecimal("5000"),new BigDecimal("5000"),z,null,null,null,z,z,null,null,null);
        inputs.add(companyId,userId,new PayrollPeriodInputService.Input(userId,dec,dec.withDayOfMonth(31),source,true,new PayrollPeriodData(List.of(),null,null,null,null,null,p)));
    }
    void historicalReceipt() {
        tx.executeWithoutResult(s->{var dec=LocalDate.of(2025,12,1);var z=BigDecimal.ZERO;
            rule=PayrollTestTerms.change(rule,Map.of("entryDate","2025-12-01","contract",Map.of("exitDate","2025-12-31","salaryProration","THIRTY_DAYS","pensionProration","FULL","thirteenthFrequency","NONE")));
            terms.add(companyId,userId,new PayrollTermsService.Input(userId,dec,"2025 contract",rule));terms.add(companyId,userId,new PayrollTermsService.Input(userId,january,"Actual post-employment status",rule));
            configuration.addInsurance(companyId,userId,new PayrollConfigurationService.InsuranceInput(dec,"2025 policy",z,z,z,z,z,z,z,z));configuration.addPension(companyId,userId,new PayrollConfigurationService.PensionInput(userId,dec,"2025 pension",z,z));historicalBaseline("Original 2025 ledger","0");
            var march=january.plusMonths(2);inputs.add(companyId,userId,new PayrollPeriodInputService.Input(userId,march,march.withDayOfMonth(31),"Historical receipt",true,new PayrollPeriodData(List.of(),null,null,null,List.of(new PayrollPayment("Historical race","REGULAR_ARREARS",dec,dec.withDayOfMonth(31),march.withDayOfMonth(15),dec.withDayOfMonth(31),new BigDecimal("500"),true,z,z)))));
        });
    }
    @Test void waitingApprovalSeesChanged2025OriginalMonthlyFactsAfterOpeningAnOldSnapshot() throws Exception {
        historicalReceipt();Long draft=draft(january.plusMonths(2));race(draft,()->historicalBaseline("Corrected original ledger source","0"));
    }
    @Test void waitingApprovalSeesChanged2025InsuranceInsteadOfItsOldConsistentSnapshot() throws Exception {
        historicalReceipt();Long draft=draft(january.plusMonths(2));race(draft,()->{var z=BigDecimal.ZERO;configuration.addInsurance(companyId,userId,new PayrollConfigurationService.InsuranceInput(LocalDate.of(2025,12,1),"Corrected 2025 insurance",z,new BigDecimal("0.01"),z,z,z,z,z,z));});
    }
    void race(Long id,Runnable update) throws Exception {
        var firstLocked=new CountDownLatch(1);var oldSnapshotOpened=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->tx.executeWithoutResult(s->{users.findByIdForUpdate(userId).orElseThrow();update.run();firstLocked.countDown();await(oldSnapshotOpened);}));
            var second=pool.submit(()->{await(firstLocked);assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{
                var draft=slips.findById(id).orElseThrow();slips.findByUserAndApproved(draft.getUser(),true);oldSnapshotOpened.countDown();payroll.approvePayslip(id,"must see new inputs");
            }));});
            first.get(45,TimeUnit.SECONDS);second.get(45,TimeUnit.SECONDS);
            tx.executeWithoutResult(s->assertFalse(slips.findById(id).orElseThrow().isApproved()));
        } finally {pool.shutdownNow();}
    }
    static void await(CountDownLatch latch){try{assertTrue(latch.await(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
