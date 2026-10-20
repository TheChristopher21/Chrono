package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.services.payroll.*;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in only: use an empty, disposable local schema, never an application database. */
@EnabledIfSystemProperty(named = "chrono.test.payroll.mysql.url", matches = ".+")
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PayrollService.class, PayrollCorrectionService.class, PayrollCalculationService.class, PayrollLedgerService.class,
        PayrollApprovalGuard.class, TaxCalculationService.class, SwissSourceTax.class, PayrollTermsIntegrationTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayrollCorrectionMySqlConcurrencyTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("chrono.test.payroll.mysql.url");
        assertNotNull(url);
        assertTrue(url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/chrono_payroll_concurrency_test[a-z0-9_]*(\\?.*)?"),
                "Concurrency tests may only recreate a disposable local chrono_payroll_concurrency_test schema");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getProperty("chrono.test.mysql.username", "root"));
        properties.add("spring.datasource.password", () -> System.getProperty("chrono.test.mysql.password", ""));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
    }
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
    @Autowired PlatformTransactionManager transactions;
    TransactionTemplate transaction;
    Long userId;
    Map<Integer, PayrollTerms> rules;

    @BeforeEach void setup() {
        transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        userId = transaction.execute(status -> {
            var company = companies.saveAndFlush(new Company("MySQL correction concurrency fixture"));
            var user = new User(); user.setUsername("correction-race-" + company.getId()); user.setPersonnelNumber("fixture-" + company.getId());
            user.setPassword("test-only"); user.setCountry("CH"); user.setCompany(company);
            user.setIsHourly(false); user.setDailyWorkHours(8.0); user.setExpectedWorkDays(5); user.setTrackingBalanceInMinutes(0);
            return users.saveAndFlush(user).getId();
        });
        rules = new HashMap<>();
        for (int month = 1; month <= 12; month++) rules.put(month, PayrollExtendedCalculationTest.terms("VD", "ANNUAL"));
        when(terms.resolve(any(), any(), any())).thenAnswer(i -> new PayrollTermsService.Resolved(1L, rules.get(((LocalDate) i.getArgument(1)).getMonthValue())));
        when(configuration.resolve(any(), any(), any())).thenReturn(PayrollExtendedCalculationTest.coverage());
        when(employment.resolveUserSnapshotsForRange(any(), any(), any())).thenAnswer(i -> Map.of(LocalDate.of(2026, 1, 1), i.getArgument(0)));
        when(tracking.getUserPeriodSummary(any(), any(), any())).thenAnswer(i -> new TimePeriodSummaryDTO("fixture", i.getArgument(1), i.getArgument(2), 0, 0, 0, 0, 0, List.of()));
        when(pdf.generatePayslipPdf(any())).thenAnswer(i -> "fixture-" + ((Payslip) i.getArgument(0)).getId() + ".pdf");
    }
    Long create(int month, boolean approve) {
        return transaction.execute(status -> {
            var start = LocalDate.of(2026, month, 1);
            var slip = payroll.generatePayslip(userId, start, start.withDayOfMonth(start.lengthOfMonth()), null);
            if (approve) payroll.approvePayslip(slip.getId(), "fixture");
            return slip.getId();
        });
    }
    List<Long> correct(Long original) {
        return transaction.execute(status -> corrections.generate(original,
                new PayrollCorrectionService.Request("Concurrency fixture", LocalDate.of(2026, 10, 16), null), "fixture")
                .stream().map(Payslip::getId).toList());
    }
    @Test void concurrentCorrectionsCannotBothApproveTheSameBaselineAfterWaitingForTheEmployeeLock() throws Exception {
        Long original = create(1, true);
        rules.put(1, PayrollTestTerms.change(rules.get(1), Map.of("amount", "6000")));
        var first = correct(original); var competing = correct(original);
        race(competing.get(0), () -> payroll.approveBatch(first, "first"),
                () -> payroll.approveBatch(competing, "stale"), true);
        transaction.executeWithoutResult(status -> {
            assertTrue(slips.findById(first.get(0)).orElseThrow().isApproved());
            assertFalse(slips.findById(competing.get(0)).orElseThrow().isApproved());
            assertEquals(first.get(0), ledger.latest(slips.findById(original).orElseThrow()).getId());
            assertEquals(5000.0, slips.findById(original).orElseThrow().getGrossSalary());
        });
    }
    @Test void newlyApprovedFollowingMonthInvalidatesTheWholeEarlierCorrectionBatch() throws Exception {
        Long original = create(1, true);
        Long february = create(2, false);
        var correction = correct(original);
        race(correction.get(0), () -> payroll.approvePayslip(february, "new following month"),
                () -> payroll.approveBatch(correction, "incomplete history"), true);
        transaction.executeWithoutResult(status -> {
            assertTrue(slips.findById(february).orElseThrow().isApproved());
            assertFalse(slips.findById(correction.get(0)).orElseThrow().isApproved());
        });
    }
    @Test void correctionGeneratedAfterWaitingUsesTheNewlyApprovedRevision() throws Exception {
        Long original = create(1, true);
        rules.put(1, PayrollTestTerms.change(rules.get(1), Map.of("amount", "6000")));
        var first = correct(original);
        AtomicReference<List<Long>> generated = new AtomicReference<>();
        race(original, () -> payroll.approveBatch(first, "first"), () -> generated.set(correct(original)), false);
        transaction.executeWithoutResult(status -> {
            var next = slips.findById(generated.get().get(0)).orElseThrow();
            assertEquals(first.get(0), next.getSupersedesCorrectionId());
            assertEquals(first.get(0), ledger.read(next).correction().baseline().revisionId());
            assertEquals(0.0, next.getGrossSalary());
            payroll.approveBatch(generated.get(), "current baseline");
        });
    }
    private void race(Long secondPreloadedId, Runnable firstWork, Runnable secondWork, boolean rejectSecond) throws Exception {
        var firstLocked = new CountDownLatch(1); var secondSnapshotOpened = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                firstWork.run(); firstLocked.countDown(); await(secondSnapshotOpened);
            }));
            var second = executor.submit(() -> {
                await(firstLocked);
                Runnable work = () -> transaction.executeWithoutResult(status -> {
                    var preloaded = slips.findById(secondPreloadedId).orElseThrow();
                    // Open a consistent read before the first transaction commits. The employee lock
                    // will then wait, but MySQL must not reuse this old view for the validation history.
                    slips.findByUserAndApproved(preloaded.getUser(), true);
                    secondSnapshotOpened.countDown(); secondWork.run();
                });
                if (rejectSecond) assertThrows(IllegalStateException.class, work::run); else work.run();
            });
            first.get(45, TimeUnit.SECONDS); second.get(45, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(20, TimeUnit.SECONDS), "Concurrent transaction did not reach its synchronization point"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
