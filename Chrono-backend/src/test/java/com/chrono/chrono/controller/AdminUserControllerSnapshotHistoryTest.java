package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.EmploymentModelType;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.EmploymentModelHistoryService;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerSnapshotHistoryTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TimeTrackingService timeTrackingService;
    @Mock
    private EmploymentModelHistoryService employmentModelHistoryService;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private Principal principal;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController();
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(controller, "companyRepository", companyRepository);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(controller, "timeTrackingService", timeTrackingService);
        ReflectionTestUtils.setField(controller, "employmentModelHistoryService", employmentModelHistoryService);
        ReflectionTestUtils.setField(controller, "userPermissionService", userPermissionService);
    }

    @Test
    void updateUser_snapshotOnlyChangeWhenBaselineIsNeeded_createsBaselineBeforeSnapshotRow() {
        when(principal.getName()).thenReturn("admin");

        Company company = new Company();
        company.setId(1L);

        Role adminRole = new Role("ROLE_ADMIN");
        User adminUser = new User();
        adminUser.setId(100L);
        adminUser.setUsername("admin");
        adminUser.setCompany(company);
        adminUser.setRoles(Set.of(adminRole));

        User existingUser = new User();
        existingUser.setId(200L);
        existingUser.setUsername("worker");
        existingUser.setCompany(company);
        existingUser.setRoles(Set.of(new Role("ROLE_USER")));
        existingUser.setCountry("DE");
        existingUser.setTaxClass("1");
        existingUser.setPersonnelNumber("123");
        existingUser.setIsHourly(false);
        existingUser.setIsPercentage(false);
        existingUser.setDailyWorkHours(8.0);
        existingUser.setExpectedWorkDays(5);
        existingUser.setWeeklySchedule(Collections.singletonList(User.getDefaultWeeklyScheduleMap()));

        UserDTO dto = new UserDTO();
        dto.setId(existingUser.getId());
        dto.setUsername(existingUser.getUsername());
        dto.setFirstName("A");
        dto.setLastName("B");
        dto.setCountry("DE");
        dto.setTaxClass("1");
        dto.setPersonnelNumber("123");
        dto.setEmail("a@b.c");
        dto.setMobilePhone("123");
        dto.setDailyWorkHours(7.5);
        dto.setExpectedWorkDays(5);
        dto.setIsHourly(false);
        dto.setIsPercentage(false);
        dto.setEmploymentModelEffectiveFrom(LocalDate.of(2026, 3, 1));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(existingUser.getId())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employmentModelHistoryService.deriveCurrentModel(any(User.class))).thenReturn(EmploymentModelType.STANDARD);
        when(employmentModelHistoryService.resolveUserSnapshotForDate(any(User.class), any(LocalDate.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(employmentModelHistoryService.needsBaselineBefore(any(User.class), any(LocalDate.class))).thenReturn(true);

        ResponseEntity<?> response = controller.updateUser(dto, null, principal);

        assertEquals(200, response.getStatusCode().value());
        verify(employmentModelHistoryService).ensureBaselineEntry(any(User.class), eq(EmploymentModelType.STANDARD), any(LocalDate.class), any(User.class));
        verify(employmentModelHistoryService).recordSnapshotChange(any(User.class), eq(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void getAllUsers_withoutCompany_returnsForbiddenInsteadOfAllUsers() {
        when(principal.getName()).thenReturn("admin");

        User adminUser = new User();
        adminUser.setUsername("admin");
        adminUser.getRoles().add(new Role("ROLE_ADMIN"));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        ResponseEntity<?> response = controller.getAllUsers(principal);

        assertEquals(403, response.getStatusCode().value());
        verify(userRepository, never()).findByDeletedFalse();
    }

    @Test
    void getAllUsers_superAdminUsesOperationalUserList() {
        when(principal.getName()).thenReturn("superadmin");

        User superAdmin = new User();
        superAdmin.setUsername("superadmin");
        superAdmin.setRoles(Set.of(new Role("ROLE_SUPERADMIN")));

        User visibleAdmin = new User();
        visibleAdmin.setId(200L);
        visibleAdmin.setUsername("tenant-admin");
        visibleAdmin.setRoles(Set.of(new Role("ROLE_ADMIN")));

        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));
        when(userRepository.findOperationalUsersDeletedFalse()).thenReturn(List.of(visibleAdmin));

        ResponseEntity<List<UserDTO>> response = controller.getAllUsers(principal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("tenant-admin", response.getBody().get(0).getUsername());
        verify(userRepository).findOperationalUsersDeletedFalse();
        verify(userRepository, never()).findByDeletedFalse();
    }

    @Test
    void updateUser_sameCompanyAdminCanUpdateAdminUser() {
        when(principal.getName()).thenReturn("admin");

        Company company = new Company();
        company.setId(1L);

        Role adminRole = new Role("ROLE_ADMIN");

        User adminUser = new User();
        adminUser.setId(100L);
        adminUser.setUsername("admin");
        adminUser.setCompany(company);
        adminUser.getRoles().add(adminRole);

        User targetAdmin = new User();
        targetAdmin.setId(200L);
        targetAdmin.setUsername("team-admin");
        targetAdmin.setCompany(company);
        targetAdmin.getRoles().add(adminRole);
        targetAdmin.setCountry("CH");
        targetAdmin.setTarifCode("A0");
        targetAdmin.setPersonnelNumber("90");
        targetAdmin.setEmail("old-admin@example.test");
        targetAdmin.setMobilePhone("123");
        targetAdmin.setIsHourly(false);
        targetAdmin.setIsPercentage(false);
        targetAdmin.setDailyWorkHours(8.5);
        targetAdmin.setExpectedWorkDays(5);
        targetAdmin.setWeeklySchedule(Collections.singletonList(User.getDefaultWeeklyScheduleMap()));

        UserDTO dto = new UserDTO();
        dto.setId(targetAdmin.getId());
        dto.setUsername(targetAdmin.getUsername());
        dto.setFirstName("Team");
        dto.setLastName("Admin");
        dto.setCountry("CH");
        dto.setTarifCode("A0");
        dto.setPersonnelNumber("90");
        dto.setEmail("new-admin@example.test");
        dto.setMobilePhone("456");
        dto.setRoles(List.of("ROLE_ADMIN"));
        dto.setDailyWorkHours(8.5);
        dto.setExpectedWorkDays(5);
        dto.setIsHourly(false);
        dto.setIsPercentage(false);
        dto.setEmploymentModelEffectiveFrom(LocalDate.of(2026, 5, 5));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(employmentModelHistoryService.deriveCurrentModel(any(User.class))).thenReturn(EmploymentModelType.STANDARD);
        when(employmentModelHistoryService.resolveUserSnapshotForDate(any(User.class), any(LocalDate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.updateUser(dto, null, principal);

        assertEquals(200, response.getStatusCode().value());
        UserDTO body = (UserDTO) response.getBody();
        assertEquals("new-admin@example.test", body.getEmail());
        verify(userRepository).save(targetAdmin);
        verify(timeTrackingService).rebuildUserBalance(targetAdmin);
    }

    @Test
    void updateTimeTrackingVisibility_sameCompanyAdminCanUpdatePrivilegedUser() {
        when(principal.getName()).thenReturn("admin");

        Company company = new Company();
        company.setId(1L);

        User adminUser = new User();
        adminUser.setId(100L);
        adminUser.setUsername("admin");
        adminUser.setCompany(company);
        adminUser.setRoles(Set.of(new Role("ROLE_ADMIN")));

        User targetUser = new User();
        targetUser.setId(200L);
        targetUser.setUsername("team-admin");
        targetUser.setCompany(company);
        targetUser.setRoles(Set.of(new Role("ROLE_ADMIN")));
        targetUser.setIncludeInTimeTracking(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.updateTimeTrackingVisibility(
                targetUser.getId(),
                Map.of("includeInTimeTracking", false),
                principal
        );

        assertEquals(200, response.getStatusCode().value());
        UserDTO body = (UserDTO) response.getBody();
        assertEquals(false, body.getIncludeInTimeTracking());
        verify(userRepository).save(targetUser);
    }

    @Test
    void updateTimeTrackingVisibility_otherCompanyUserIsForbidden() {
        when(principal.getName()).thenReturn("admin");

        Company adminCompany = new Company();
        adminCompany.setId(1L);
        Company otherCompany = new Company();
        otherCompany.setId(2L);

        User adminUser = new User();
        adminUser.setUsername("admin");
        adminUser.setCompany(adminCompany);
        adminUser.setRoles(Set.of(new Role("ROLE_ADMIN")));

        User targetUser = new User();
        targetUser.setId(200L);
        targetUser.setUsername("other-admin");
        targetUser.setCompany(otherCompany);
        targetUser.setRoles(Set.of(new Role("ROLE_ADMIN")));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        ResponseEntity<?> response = controller.updateTimeTrackingVisibility(
                targetUser.getId(),
                Map.of("includeInTimeTracking", false),
                principal
        );

        assertEquals(403, response.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
    }
}
