package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import(PayrollApprovalGuard.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayrollApprovalConcurrencyTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean PayrollCalculationService calculations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean TimeTrackingService tracking;
    @Autowired PayrollApprovalGuard guard;
    @Autowired PayslipRepository slips;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired PlatformTransactionManager manager;

    @Test void concurrentOverlappingApprovalsDoNotBothPassAfterLoadingStaleEntities() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        List<Long> ids = transaction.execute(status -> {
            Company company = companies.save(new Company("Concurrency fixture"));
            User user = new User(); user.setCompany(company); user.setUsername("concurrency-fixture");
            user.setPassword("test-only"); user.setCountry("CH"); user.setPersonnelNumber("fixture");
            user = users.saveAndFlush(user);
            return List.of(slips.saveAndFlush(draft(user)).getId(), slips.saveAndFlush(draft(user)).getId());
        });
        CountDownLatch firstLocked = new CountDownLatch(1), secondLoaded = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                Payslip slip = slips.findById(ids.get(0)).orElseThrow();
                guard.validate(List.of(slip));
                slip.setApproved(true); slips.saveAndFlush(slip);
                firstLocked.countDown(); await(secondLoaded);
            }));
            Future<?> second = executor.submit(() -> {
                await(firstLocked);
                assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                    Payslip slip = slips.findById(ids.get(1)).orElseThrow();
                    slip.getUser().getTrackingBalanceInMinutes();
                    secondLoaded.countDown();
                    guard.validate(List.of(slip));
                    slip.setApproved(true); slips.saveAndFlush(slip);
                }));
            });
            first.get(30,TimeUnit.SECONDS); second.get(30,TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> {
                assertTrue(slips.findById(ids.get(0)).orElseThrow().isApproved());
                assertFalse(slips.findById(ids.get(1)).orElseThrow().isApproved());
            });
        } finally { executor.shutdownNow(); }
    }
    private static Payslip draft(User user) {
        Payslip slip = new Payslip(); slip.setUser(user);
        slip.setPeriodStart(LocalDate.of(2026,1,1)); slip.setPeriodEnd(LocalDate.of(2026,1,31));
        slip.setGrossSalary(100.0); slip.setDeductions(10.0); slip.setNetSalary(90.0); slip.setEmployerContributions(0.0);
        slip.getEarnings().add(new PayComponent("Lohn",100.0)); slip.getDeductionsList().add(new PayComponent("Abzug",10.0));
        return slip;
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10,TimeUnit.SECONDS), "Concurrent transaction did not reach its synchronization point"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
