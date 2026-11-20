package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;
import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.JournalEntryLine;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.inventory.ProductRepository;
import com.chrono.chrono.repositories.inventory.StockLevelRepository;
import com.chrono.chrono.repositories.inventory.WarehouseRepository;
import com.chrono.chrono.utils.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoLoginIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private ScheduleRuleRepository scheduleRuleRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private DemoDataService demoDataService;

    @Autowired
    private AccountingService accountingService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    @Test
    void demoLoginSeedsDemoAccountAndDemoData() {
        AuthResponse response = authService.demoLogin();

        assertNotNull(response.getToken());

        String username = jwtUtil.extractUsername(response.getToken());
        User demoUser = userRepository.findByUsername(username).orElseThrow();
        assertTrue(demoUser.isDemo());
        assertTrue(demoUser.getUsername().startsWith("demo_"));
        assertNotNull(demoUser.getDemoSessionId());
        assertNotNull(demoUser.getDemoExpiresAt());
        assertNotNull(demoUser.getCompany());
        assertTrue(demoUser.getCompany().isDemo());
        assertEquals(demoUser.getDemoSessionId(), demoUser.getCompany().getDemoSessionId());
        assertFalse(customerRepository.findAll().isEmpty());
        assertFalse(projectRepository.findAll().isEmpty());
        assertFalse(timeTrackingEntryRepository.findAll().isEmpty());
        assertFalse(scheduleEntryRepository.findAll().isEmpty());
        assertFalse(payslipRepository.findAll().isEmpty());
        assertFalse(productRepository.findAllByCompany_Id(demoUser.getCompany().getId()).isEmpty());
        assertFalse(warehouseRepository.findAllByCompany_Id(demoUser.getCompany().getId()).isEmpty());
        assertFalse(stockLevelRepository.findAllByProduct_Company_Id(demoUser.getCompany().getId()).isEmpty());
        assertTrue(demoUser.getCompany().getEnabledFeatures().contains("supplyChain"));
        assertTrue(demoUser.getCompany().getEnabledFeatures().contains("payroll"));
        assertTrue(demoUser.getCompany().getEnabledFeatures().contains("roster"));
    }

    @Test
    void demoLoginCreatesSeparateTenantPerVisitor() {
        String firstUsername = jwtUtil.extractUsername(authService.demoLogin().getToken());
        String secondUsername = jwtUtil.extractUsername(authService.demoLogin().getToken());

        User first = userRepository.findByUsername(firstUsername).orElseThrow();
        User second = userRepository.findByUsername(secondUsername).orElseThrow();

        assertNotEquals(first.getUsername(), second.getUsername());
        assertNotEquals(first.getCompany().getId(), second.getCompany().getId());
        assertNotEquals(first.getDemoSessionId(), second.getDemoSessionId());
    }

    @Test
    void cleanupExpiredDemoTenantsDeletesSeededDemoData() {
        String username = jwtUtil.extractUsername(authService.demoLogin().getToken());
        User demoUser = userRepository.findByUsername(username).orElseThrow();
        Company company = demoUser.getCompany();
        Long companyId = company.getId();
        assertFalse(scheduleRuleRepository.findByCompany_IdOrderByStartTime(companyId).isEmpty());
        ScheduleRule shared = shiftDefinition("KEEP_DEFAULT", null);
        scheduleRuleRepository.save(shared);
        Company retainedCompany = companyRepository.save(new Company("Retained company"));
        ScheduleRule retainedOwned = scheduleRuleRepository.save(shiftDefinition("EARLY", retainedCompany));
        AccountingGraph expiredAccounting = financialGraph(company, "expired-demo");
        AccountingGraph sharedAccounting = financialGraph(null, "shared-demo-history");
        AccountingGraph retainedAccounting = financialGraph(retainedCompany, "retained-normal");

        company.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));
        companyRepository.saveAndFlush(company);
        entityManager.flush();
        entityManager.clear();

        int deleted = demoDataService.cleanupExpiredDemoTenants();
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, deleted);
        assertTrue(userRepository.findByUsername(username).isEmpty());
        assertTrue(companyRepository.findById(companyId).isEmpty());
        assertTrue(scheduleRuleRepository.findByCompany_IdOrderByStartTime(companyId).isEmpty());
        assertTrue(scheduleRuleRepository.findById(shared.getId()).isPresent());
        assertTrue(scheduleRuleRepository.findById(retainedOwned.getId()).isPresent());
        assertTrue(companyRepository.findById(retainedCompany.getId()).isPresent());
        assertAccountingDeleted(expiredAccounting);
        assertAccountingPreserved(sharedAccounting);
        assertAccountingPreserved(retainedAccounting);
    }

    @Test
    void cleanupExpiredDemoTenantAfterReadingItsAccountsRemovesOnlyItsSeededChart() {
        String username = jwtUtil.extractUsername(authService.demoLogin().getToken());
        Company company = userRepository.findByUsername(username).orElseThrow().getCompany();
        Long companyId = company.getId();
        List<Long> seededIds = accountingService.listAccounts(company).stream().map(Account::getId).toList();
        assertEquals(12, seededIds.size());
        Company retainedCompany = companyRepository.save(new Company("Normal company with an old expiry date"));
        retainedCompany.setDemoExpiresAt(LocalDateTime.now().minusDays(2));
        List<Long> retainedIds = accountingService.listAccounts(retainedCompany).stream().map(Account::getId).toList();
        Account shared = new Account("1000", "Historical global bank", AccountType.ASSET);
        entityManager.persist(shared);
        Long sharedId = shared.getId();
        company.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));
        companyRepository.saveAndFlush(company);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, demoDataService.cleanupExpiredDemoTenants());
        entityManager.flush();
        entityManager.clear();

        assertTrue(companyRepository.findById(companyId).isEmpty());
        seededIds.forEach(id -> assertNull(entityManager.find(Account.class, id)));
        retainedIds.forEach(id -> assertNotNull(entityManager.find(Account.class, id)));
        assertNotNull(entityManager.find(Account.class, sharedId));
        assertTrue(companyRepository.findById(retainedCompany.getId()).isPresent());
    }

    private record AccountingGraph(List<Long> accounts, List<Long> lines, Long journal, Long asset,
                                   Long customerInvoice, Long vendorInvoice, Long company) {}

    private AccountingGraph financialGraph(Company company, String prefix) {
        Account bank = new Account("1000", prefix + " bank", AccountType.ASSET);
        bank.setCompany(company);
        bank.setNotes(prefix + " original notes");
        entityManager.persist(bank);
        Account expense = new Account("5000", prefix + " expense", AccountType.EXPENSE);
        expense.setCompany(company);
        entityManager.persist(expense);
        Account inactiveChild = new Account("1001", prefix + " inactive child", AccountType.ASSET);
        inactiveChild.setCompany(company);
        inactiveChild.setParent(bank);
        inactiveChild.setActive(false);
        entityManager.persist(inactiveChild);

        JournalEntry journal = new JournalEntry();
        journal.setCompany(company);
        journal.setEntryDate(LocalDate.of(2026, 10, 31));
        journal.setSource("MANUAL");
        journal.setDocumentReference(prefix + " original reference");
        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(expense);
        debit.setDebit(new BigDecimal("25.1250"));
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo(prefix + " original debit");
        journal.addLine(debit);
        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(bank);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("25.1250"));
        credit.setMemo(prefix + " original credit");
        journal.addLine(credit);
        entityManager.persist(journal);

        Asset asset = new Asset();
        asset.setCompany(company);
        asset.setAssetName(prefix + " original asset");
        asset.setAcquisitionDate(LocalDate.of(2026, 1, 17));
        asset.setAcquisitionCost(new BigDecimal("25.1250"));
        asset.setUsefulLifeMonths(24);
        asset.setAcquisitionEntry(journal);
        entityManager.persist(asset);
        CustomerInvoice receivable = new CustomerInvoice();
        receivable.setCompany(company);
        receivable.setCustomerName(prefix + " customer");
        receivable.setInvoiceNumber(prefix + "-AR");
        receivable.setAmount(new BigDecimal("25.1250"));
        receivable.setJournalEntry(journal);
        entityManager.persist(receivable);
        VendorInvoice payable = new VendorInvoice();
        payable.setCompany(company);
        payable.setVendorName(prefix + " vendor");
        payable.setInvoiceNumber(prefix + "-AP");
        payable.setAmount(new BigDecimal("25.1250"));
        payable.setJournalEntry(journal);
        entityManager.persist(payable);
        entityManager.flush();
        return new AccountingGraph(List.of(bank.getId(), expense.getId(), inactiveChild.getId()),
                List.of(debit.getId(), credit.getId()), journal.getId(), asset.getId(), receivable.getId(),
                payable.getId(), company == null ? null : company.getId());
    }

    private void assertAccountingDeleted(AccountingGraph graph) {
        graph.accounts().forEach(id -> assertNull(entityManager.find(Account.class, id)));
        graph.lines().forEach(id -> assertNull(entityManager.find(JournalEntryLine.class, id)));
        assertNull(entityManager.find(JournalEntry.class, graph.journal()));
        assertNull(entityManager.find(Asset.class, graph.asset()));
        assertNull(entityManager.find(CustomerInvoice.class, graph.customerInvoice()));
        assertNull(entityManager.find(VendorInvoice.class, graph.vendorInvoice()));
    }

    private void assertAccountingPreserved(AccountingGraph graph) {
        graph.accounts().forEach(id -> {
            Account account = entityManager.find(Account.class, id);
            assertNotNull(account);
            assertEquals(graph.company(), account.getCompany() == null ? null : account.getCompany().getId());
        });
        Account bank = entityManager.find(Account.class, graph.accounts().get(0));
        assertTrue(bank.getNotes().endsWith(" original notes"));
        Account child = entityManager.find(Account.class, graph.accounts().get(2));
        assertFalse(child.isActive());
        assertEquals(bank.getId(), child.getParent().getId());
        JournalEntry journal = entityManager.find(JournalEntry.class, graph.journal());
        assertNotNull(journal);
        assertEquals(graph.company(), journal.getCompany() == null ? null : journal.getCompany().getId());
        assertTrue(journal.getDocumentReference().endsWith(" original reference"));
        assertEquals(2, journal.getLines().size());
        graph.lines().forEach(id -> assertNotNull(entityManager.find(JournalEntryLine.class, id)));
        Asset asset = entityManager.find(Asset.class, graph.asset());
        assertNotNull(asset);
        assertEquals(graph.journal(), asset.getAcquisitionEntry().getId());
        assertEquals(0, new BigDecimal("25.1250").compareTo(asset.getAcquisitionCost()));
        CustomerInvoice receivable = entityManager.find(CustomerInvoice.class, graph.customerInvoice());
        VendorInvoice payable = entityManager.find(VendorInvoice.class, graph.vendorInvoice());
        assertNotNull(receivable);
        assertNotNull(payable);
        assertEquals(graph.journal(), receivable.getJournalEntry().getId());
        assertEquals(graph.journal(), payable.getJournalEntry().getId());
        assertEquals(0, new BigDecimal("25.1250").compareTo(receivable.getAmount()));
        assertEquals(0, new BigDecimal("25.1250").compareTo(payable.getAmount()));
    }

    private ScheduleRule shiftDefinition(String key, Company company) {
        ScheduleRule rule = new ScheduleRule();
        rule.setShiftKey(key); rule.setLabel("Retained definition");
        rule.setStartTime("08:00"); rule.setEndTime("16:00"); rule.setCompany(company);
        return rule;
    }
}
