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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
