package com.chrono.chrono.controller;

import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.TimeTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayslipTimeSummaryTest {
    private final UserRepository users = mock(UserRepository.class);
    private final TimeTrackingService tracking = mock(TimeTrackingService.class);
    private final PayslipController controller = new PayslipController();
    private final LocalDate start = LocalDate.of(2026, 8, 1);
    private final LocalDate end = LocalDate.of(2026, 8, 31);
    private User actor;
    private User employee;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);
        actor = new User();
        actor.setId(1L);
        actor.setCompany(company);
        Role role = new Role();
        role.setRoleName("ROLE_PAYROLL_ADMIN");
        actor.setRoles(Set.of(role));
        employee = new User();
        employee.setId(2L);
        employee.setCompany(company);
        employee.setTrackingBalanceInMinutes(1001);
        when(users.findByUsername("payroll")).thenReturn(Optional.of(actor));
        when(users.findById(2L)).thenReturn(Optional.of(employee));
        ReflectionTestUtils.setField(controller, "userRepository", users);
        ReflectionTestUtils.setField(controller, "timeTrackingService", tracking);
        ReflectionTestUtils.setField(controller, "accessControlService", new AccessControlService(users));
    }

    @Test
    void returnsCurrentBalanceWithoutRequiringDates() {
        var summary = controller.timeSummary(2L, null, null, () -> "payroll").getBody();
        assertEquals(1001, summary.currentBalanceMinutes());
        assertNull(summary.differenceMinutes());
        verifyNoInteractions(tracking);
    }

    @Test
    void usesTheTimeOverviewForPeriodTotalsAndPreservesNegativeBalances() {
        when(tracking.getUserPeriodSummary(employee, start, end)).thenReturn(
                new TimePeriodSummaryDTO("employee", start, end, 9500, 600, 9600, -100, 1001, List.of()));
        var summary = controller.timeSummary(2L, start, end, () -> "payroll").getBody();
        assertEquals(1001, summary.currentBalanceMinutes());
        assertEquals(9500, summary.workedMinutes());
        assertEquals(9600, summary.expectedMinutes());
        assertEquals(-100, summary.differenceMinutes());
    }

    @Test
    void rejectsAnotherCompanyBeforeLoadingTimeData() {
        Company other = new Company();
        other.setId(2L);
        employee.setCompany(other);
        assertThrows(AccessDeniedException.class, () -> controller.timeSummary(2L, start, end, () -> "payroll"));
        verifyNoInteractions(tracking);
    }

    @Test
    void rejectsIncompleteAndReversedPeriods() {
        assertThrows(ResponseStatusException.class, () -> controller.timeSummary(2L, start, null, () -> "payroll"));
        assertThrows(ResponseStatusException.class, () -> controller.timeSummary(2L, null, end, () -> "payroll"));
        assertThrows(ResponseStatusException.class, () -> controller.timeSummary(2L, end, start, () -> "payroll"));
        verifyNoInteractions(tracking);
    }
}
