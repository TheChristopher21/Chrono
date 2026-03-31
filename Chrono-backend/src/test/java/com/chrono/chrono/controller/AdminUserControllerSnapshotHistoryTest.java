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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
