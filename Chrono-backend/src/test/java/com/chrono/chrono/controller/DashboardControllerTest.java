package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private UserRepository userRepository;

    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        dashboardController = new DashboardController(
                dashboardService,
                new AccessControlService(userRepository)
        );
    }

    @Test
    void getUserDashboard_allowsSelfAndParsesIsoDates() {
        DashboardResponse expected = new DashboardResponse();
        User actor = user(1L, "demo-admin", company(10L), "ROLE_USER");
        when(userRepository.findByUsernameWithPermissionContext("demo-admin")).thenReturn(Optional.of(actor));
        when(dashboardService.getUserDashboardForWeek("demo-admin", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(expected);

        DashboardResponse actual = dashboardController.getUserDashboard(
                "demo-admin",
                "2024-01-01",
                "2024-01-07",
                principal("demo-admin")
        );

        assertSame(expected, actual);
        verify(dashboardService).getUserDashboardForWeek("demo-admin", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));
    }

    @Test
    void getUserDashboard_allowsSameCompanyAdmin() {
        Company company = company(10L);
        User actor = user(1L, "admin", company, "ROLE_ADMIN");
        User target = user(2L, "worker", company, "ROLE_USER");
        DashboardResponse expected = new DashboardResponse();
        when(userRepository.findByUsernameWithPermissionContext("admin")).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameWithPermissionContext("worker")).thenReturn(Optional.of(target));
        when(dashboardService.getUserDashboardForWeek("worker", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(expected);

        DashboardResponse actual = dashboardController.getUserDashboard(
                "worker", "2024-01-01", "2024-01-07", principal("admin"));

        assertSame(expected, actual);
    }

    @Test
    void getUserDashboard_allowsSuperadminAcrossCompanies() {
        User actor = user(1L, "root", null, "ROLE_SUPERADMIN");
        User target = user(2L, "worker", company(20L), "ROLE_USER");
        DashboardResponse expected = new DashboardResponse();
        when(userRepository.findByUsernameWithPermissionContext("root")).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameWithPermissionContext("worker")).thenReturn(Optional.of(target));
        when(dashboardService.getUserDashboardForWeek("worker", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(expected);

        DashboardResponse actual = dashboardController.getUserDashboard(
                "worker", "2024-01-01", "2024-01-07", principal("root"));

        assertSame(expected, actual);
    }

    @Test
    void getUserDashboard_deniesOrdinaryPeerEvenInSameCompany() {
        Company company = company(10L);
        User actor = user(1L, "alice", company, "ROLE_USER");
        User target = user(2L, "bob", company, "ROLE_USER");
        when(userRepository.findByUsernameWithPermissionContext("alice")).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameWithPermissionContext("bob")).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () -> dashboardController.getUserDashboard(
                "bob", "2024-01-01", "2024-01-07", principal("alice")));

        verify(dashboardService, never()).getUserDashboardForWeek(
                "bob", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));
    }

    @Test
    void getUserDashboard_deniesAdminFromAnotherCompany() {
        User actor = user(1L, "admin", company(10L), "ROLE_ADMIN");
        User target = user(2L, "worker", company(20L), "ROLE_USER");
        when(userRepository.findByUsernameWithPermissionContext("admin")).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameWithPermissionContext("worker")).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () -> dashboardController.getUserDashboard(
                "worker", "2024-01-01", "2024-01-07", principal("admin")));

        verify(dashboardService, never()).getUserDashboardForWeek(
                "worker", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));
    }

    private Principal principal(String username) {
        return () -> username;
    }

    private Company company(Long id) {
        Company company = new Company("Company " + id);
        company.setId(id);
        return company;
    }

    private User user(Long id, String username, Company company, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setCompany(company);
        user.getRoles().add(new Role(roleName));
        return user;
    }
}
