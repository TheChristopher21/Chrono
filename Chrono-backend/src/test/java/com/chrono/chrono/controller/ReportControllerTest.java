package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ReportService;
import com.chrono.chrono.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    private ReportController controller;
    private ReportService reportService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        controller = new ReportController();
        reportService = mock(ReportService.class);
        userService = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "reportService", reportService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "icsFeedToken", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(controller, "publicIcsFeedWithoutToken", false);
    }

    @Test
    void feedTokenIsBoundToUsername() {
        String aliceToken = controller.feedTokenForUsername("alice");

        assertNotNull(aliceToken);
        assertTrue(controller.validFeedToken("alice", aliceToken));
        assertFalse(controller.validFeedToken("bob", aliceToken));
        assertFalse(controller.validFeedToken("alice", "0123456789abcdef0123456789abcdef"));
    }

    @Test
    void projectAnalyticsAcceptsModernProjectsFeatureWithLegacyFlagDisabled() {
        Company company = company(42L, false, Set.of("projects"));
        User user = user("admin", company, "ROLE_ADMIN");
        LocalDate start = LocalDate.of(2026, 8, 3);
        LocalDate end = LocalDate.of(2026, 9, 2);
        when(userService.getUserByUsername("admin")).thenReturn(user);
        when(reportService.getProjectAnalytics(42L, start, end)).thenReturn(List.of());

        var response = controller.projectAnalytics(start.toString(), end.toString(), () -> "admin");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(), response.getBody());
        verify(reportService).getProjectAnalytics(42L, start, end);
    }

    @Test
    void projectAnalyticsKeepsLegacyCompaniesCompatible() {
        Company company = company(43L, true, Set.of());
        User user = user("legacy-admin", company, "ROLE_ADMIN");
        when(userService.getUserByUsername("legacy-admin")).thenReturn(user);
        when(reportService.getProjectAnalytics(43L, null, null)).thenReturn(List.of());

        var response = controller.projectAnalytics(null, null, () -> "legacy-admin");

        assertEquals(200, response.getStatusCode().value());
        verify(reportService).getProjectAnalytics(43L, null, null);
    }

    @Test
    void projectAnalyticsRejectsUnscopedSuperAdmin() {
        User user = user("root", null, "ROLE_SUPERADMIN");
        when(userService.getUserByUsername("root")).thenReturn(user);

        var response = controller.projectAnalytics(null, null, () -> "root");

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(reportService);
    }

    private static Company company(Long id, boolean legacyEnabled, Set<String> features) {
        Company company = new Company("Example AG");
        company.setId(id);
        company.setCustomerTrackingEnabled(legacyEnabled);
        company.setEnabledFeatures(features);
        return company;
    }

    private static User user(String username, Company company, String roleName) {
        User user = new User();
        user.setUsername(username);
        user.setCompany(company);
        user.getRoles().add(new Role(roleName));
        return user;
    }
}
