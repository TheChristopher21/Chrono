package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.EmploymentModelType;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.EmploymentModelHistoryService;
import com.chrono.chrono.services.StripeService;
import com.chrono.chrono.services.UserPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyManagementControllerTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StripeService stripeService;
    @Mock private UserPermissionService userPermissionService;
    @Mock private EmploymentModelHistoryService employmentModelHistoryService;

    private CompanyManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyManagementController();
        ReflectionTestUtils.setField(controller, "companyRepository", companyRepository);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(controller, "stripeService", stripeService);
        ReflectionTestUtils.setField(controller, "userPermissionService", userPermissionService);
        ReflectionTestUtils.setField(controller, "employmentModelHistoryService", employmentModelHistoryService);
    }

    @Test
    void createCompanyWithAdmin_createsCompleteRegularAdminWithPmsAccess() {
        LocalDate today = LocalDate.of(2026, 8, 4);
        Role adminRole = new Role("ROLE_ADMIN");

        when(userRepository.existsByUsername("hotel-test-admin")).thenReturn(false);
        when(passwordEncoder.encode("SehrSicher!2026")).thenReturn("encoded-password");
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(employmentModelHistoryService.currentBerlinDate()).thenReturn(today);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(41L);
            return company;
        });
        when(userPermissionService.resolvePermissionsForPersistence(any(User.class), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(77L);
            return user;
        });

        CompanyManagementController.CreateCompanyWithAdminDTO dto = validDto();
        dto.setAdminPmsAccess(true);
        dto.setCustomerTrackingEnabled(false);
        dto.setEnabledFeatures(Set.of("projects"));

        ResponseEntity<?> response = controller.createCompanyWithAdmin(dto);

        assertEquals(201, response.getStatusCode().value());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedAdmin = userCaptor.getValue();

        assertEquals("hotel-test-admin", savedAdmin.getUsername());
        assertEquals("encoded-password", savedAdmin.getPassword());
        assertEquals("encoded-password", savedAdmin.getAdminPassword());
        assertEquals("CH", savedAdmin.getCountry());
        assertEquals("A0", savedAdmin.getTarifCode());
        assertEquals("SG", savedAdmin.getCanton());
        assertEquals("HOTEL-TEST-001", savedAdmin.getPersonnelNumber());
        assertEquals("Direktion", savedAdmin.getDepartment());
        assertFalse(savedAdmin.isIncludeInTimeTracking());
        assertEquals(0, savedAdmin.getAnnualVacationDays());
        assertEquals(5, savedAdmin.getExpectedWorkDays());
        assertEquals(8.5, savedAdmin.getDailyWorkHours());
        assertEquals(today, savedAdmin.getEntryDate());
        assertNotNull(savedAdmin.getCompany());
        assertEquals(41L, savedAdmin.getCompany().getId());
        assertTrue(savedAdmin.getCompany().getEnabledFeatures().contains("pms"));
        assertTrue(savedAdmin.getCompany().getEnabledFeatures().contains("projects"));
        assertTrue(savedAdmin.getCompany().getCustomerTrackingEnabled());
        assertTrue(savedAdmin.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName())));
        assertFalse(savedAdmin.getRoles().stream().anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> permissionCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userPermissionService).resolvePermissionsForPersistence(eq(savedAdmin), permissionCaptor.capture());
        assertEquals(
                UserPermissionService.ACCESS_MANAGE,
                permissionCaptor.getValue().get(UserPermissionService.PAGE_PMS)
        );
        verify(employmentModelHistoryService)
                .ensureBaselineEntry(savedAdmin, EmploymentModelType.STANDARD, today);
    }

    @Test
    void createCompanyWithAdmin_rejectsMissingPersonnelNumberBeforeSavingCompany() {
        CompanyManagementController.CreateCompanyWithAdminDTO dto = validDto();
        dto.setAdminPersonnelNumber(" ");

        ResponseEntity<?> response = controller.createCompanyWithAdmin(dto);

        assertEquals(400, response.getStatusCode().value());
        verify(companyRepository, never()).save(any(Company.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getCompany_returnsSafeEffectiveDtoInsteadOfSerializingUsers() throws Exception {
        Company company = company(41L, true, Set.of());
        User user = new User();
        user.setUsername("sensitive-admin");
        user.setPassword("password-hash");
        user.setAdminPassword("admin-password-hash");
        user.setBankAccount("CH9300762011623852957");
        user.setSocialSecurityNumber("756.1234.5678.97");
        company.getUsers().add(user);
        when(companyRepository.findById(41L)).thenReturn(Optional.of(company));

        ResponseEntity<?> response = controller.getCompany(41L);

        assertEquals(200, response.getStatusCode().value());
        CompanyManagementController.CompanyDTO body = assertInstanceOf(
                CompanyManagementController.CompanyDTO.class,
                response.getBody()
        );
        assertTrue(body.getCustomerTrackingEnabled());
        assertTrue(body.getEnabledFeatures().contains("projects"));

        String json = new ObjectMapper().writeValueAsString(body);
        assertFalse(json.contains("users"));
        assertFalse(json.contains("password-hash"));
        assertFalse(json.contains("admin-password-hash"));
        assertFalse(json.contains("CH9300762011623852957"));
        assertFalse(json.contains("756.1234.5678.97"));
    }

    @Test
    void createCompany_defaultsToActiveWhenActiveIsOmitted() {
        CompanyManagementController.CompanyDTO dto = new CompanyManagementController.CompanyDTO();
        dto.setName("Default Active AG");
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.createCompany(dto);

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void updateCompany_partialRequestPreservesActiveAndSynchronizesCurrentAlias() {
        Company company = company(51L, true, Set.of());
        company.setActive(true);
        CompanyManagementController.CompanyDTO dto = new CompanyManagementController.CompanyDTO();
        dto.setCity("Zürich");

        ResponseEntity<?> response = updateCompany(company, dto);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(company.isActive());
        assertTrue(company.getCustomerTrackingEnabled());
        assertTrue(company.getEnabledFeatures().contains("projects"));
    }

    @Test
    void updateCompany_legacyOnlyDisableRemovesModernProjectAlias() {
        Company company = company(52L, true, Set.of("projects", "crm"));
        CompanyManagementController.CompanyDTO dto = new CompanyManagementController.CompanyDTO();
        dto.setCustomerTrackingEnabled(false);

        ResponseEntity<?> response = updateCompany(company, dto);

        assertEquals(200, response.getStatusCode().value());
        assertFalse(company.getCustomerTrackingEnabled());
        assertFalse(company.getEnabledFeatures().contains("projects"));
        assertTrue(company.getEnabledFeatures().contains("crm"));
    }

    @Test
    void updateCompany_featuresOnlyDisableClearsLegacyProjectAlias() {
        Company company = company(53L, true, Set.of("projects", "crm"));
        CompanyManagementController.CompanyDTO dto = new CompanyManagementController.CompanyDTO();
        dto.setEnabledFeatures(Set.of("crm"));

        ResponseEntity<?> response = updateCompany(company, dto);

        assertEquals(200, response.getStatusCode().value());
        assertFalse(company.getCustomerTrackingEnabled());
        assertFalse(company.getEnabledFeatures().contains("projects"));
        assertTrue(company.getEnabledFeatures().contains("crm"));
    }

    @Test
    void updateCompany_conflictingExplicitAliasesResolveToEnabled() {
        Company company = company(54L, false, Set.of());
        CompanyManagementController.CompanyDTO dto = new CompanyManagementController.CompanyDTO();
        dto.setCustomerTrackingEnabled(false);
        dto.setEnabledFeatures(Set.of("projects"));

        ResponseEntity<?> response = updateCompany(company, dto);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(company.getCustomerTrackingEnabled());
        assertTrue(company.getEnabledFeatures().contains("projects"));
    }

    private ResponseEntity<?> updateCompany(Company company, CompanyManagementController.CompanyDTO dto) {
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        return controller.updateCompany(company.getId(), dto);
    }

    private static Company company(Long id, boolean legacyProjectsEnabled, Set<String> enabledFeatures) {
        Company company = new Company("Example AG");
        company.setId(id);
        company.setCustomerTrackingEnabled(legacyProjectsEnabled);
        company.setEnabledFeatures(enabledFeatures);
        return company;
    }

    private static CompanyManagementController.CreateCompanyWithAdminDTO validDto() {
        CompanyManagementController.CreateCompanyWithAdminDTO dto =
                new CompanyManagementController.CreateCompanyWithAdminDTO();
        dto.setCompanyName("Hotel Testbetrieb");
        dto.setAdminUsername("hotel-test-admin");
        dto.setAdminPassword("SehrSicher!2026");
        dto.setAdminFirstName("Test");
        dto.setAdminLastName("Hotel");
        dto.setAdminDepartment("Direktion");
        dto.setAdminCountry("CH");
        dto.setAdminTarifCode("A0");
        dto.setAdminCanton("SG");
        dto.setAdminPersonnelNumber("HOTEL-TEST-001");
        dto.setAdminIncludeInTimeTracking(false);
        return dto;
    }
}
