package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.ScheduleEntryRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        company.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));
        companyRepository.saveAndFlush(company);
        entityManager.flush();
        entityManager.clear();

        int deleted = demoDataService.cleanupExpiredDemoTenants();

        assertEquals(1, deleted);
        assertTrue(userRepository.findByUsername(username).isEmpty());
        assertTrue(companyRepository.findById(companyId).isEmpty());
    }
}
