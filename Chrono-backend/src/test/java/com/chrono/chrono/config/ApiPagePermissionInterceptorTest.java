package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiPagePermissionInterceptorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPermissionService userPermissionService = new UserPermissionService();
    private final ApiPagePermissionInterceptor interceptor =
            new ApiPagePermissionInterceptor(userRepository, userPermissionService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesAccountingReadWhenPagePermissionIsDisabled() {
        User user = user("alice", "ROLE_ADMIN", Set.of("accounting"),
                Map.of(UserPermissionService.PAGE_ADMIN_ACCOUNTING, UserPermissionService.ACCESS_NONE));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("GET", "/api/accounting/accounts"), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsBankingPageToReadOpenAccountingPayables() {
        User user = user("alice", "ROLE_ADMIN", Set.of("banking"),
                Map.of(UserPermissionService.PAGE_BANKING, UserPermissionService.ACCESS_VIEW));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("GET", "/api/accounting/payables/open"), response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void deniesWriteRequestsWhenOnlyViewAccessIsGranted() {
        User user = user("alice", "ROLE_ADMIN", Set.of("crm"),
                Map.of(UserPermissionService.PAGE_CRM, UserPermissionService.ACCESS_VIEW));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("POST", "/api/crm/leads"), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsUserDashboardToReadOwnSickLeaves() {
        User user = user("worker", "ROLE_USER", Set.of(),
                Map.of(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.ACCESS_VIEW));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("GET", "/api/sick-leave/my"), response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void allowsUserDashboardToReportOwnSickLeave() {
        User user = user("worker", "ROLE_USER", Set.of(),
                Map.of(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.ACCESS_MANAGE));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("POST", "/api/sick-leave/report"), response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void deniesUserDashboardAccessToAdminSickLeaveWrites() {
        User user = user("worker", "ROLE_USER", Set.of(),
                Map.of(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.ACCESS_MANAGE));
        authenticate(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("DELETE", "/api/sick-leave/12"), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    @Test
    void payrollAdminReceivesPayrollAccessWithoutUserManagementAccess() {
        User user = user("payroll", "ROLE_PAYROLL_ADMIN", Set.of("payroll"), Map.of());

        assertTrue(userPermissionService.hasPageAccess(
                user,
                UserPermissionService.PAGE_ADMIN_PAYSLIPS,
                UserPermissionService.ACCESS_MANAGE));
        assertFalse(userPermissionService.hasPageAccess(
                user,
                UserPermissionService.PAGE_ADMIN_USERS,
                UserPermissionService.ACCESS_VIEW));
    }

    @Test
    void allNonePermissionsAreDetectedForDefaultBackfill() {
        User user = user("admin", "ROLE_ADMIN", Set.of(), Map.of());
        user.setPagePermissions(allNonePagePermissionsFor(user));

        Map<String, String> defaultPermissions = userPermissionService.buildDefaultPagePermissions(user);

        assertTrue(userPermissionService.needsDefaultPagePermissionBackfill(user));
        assertTrue(userPermissionService.hasPageAccess(
                user,
                UserPermissionService.PAGE_ADMIN_DASHBOARD,
                UserPermissionService.ACCESS_MANAGE));
        assertEquals(UserPermissionService.ACCESS_MANAGE,
                defaultPermissions.get(UserPermissionService.PAGE_DASHBOARD));
        assertEquals(UserPermissionService.ACCESS_MANAGE,
                defaultPermissions.get(UserPermissionService.PAGE_ADMIN_DASHBOARD));
    }

    @Test
    void singleDisabledPageDoesNotNeedDefaultBackfill() {
        User user = user("admin", "ROLE_ADMIN", Set.of("accounting"),
                Map.of(UserPermissionService.PAGE_ADMIN_ACCOUNTING, UserPermissionService.ACCESS_NONE));

        assertFalse(userPermissionService.needsDefaultPagePermissionBackfill(user));
        assertFalse(userPermissionService.hasPageAccess(
                user,
                UserPermissionService.PAGE_ADMIN_ACCOUNTING,
                UserPermissionService.ACCESS_VIEW));
    }

    private void authenticate(User user) {
        when(userRepository.findByUsernameWithPermissionContext(user.getUsername())).thenReturn(Optional.of(user));
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(user.getUsername(), "secret", "ROLE_ADMIN");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private User user(String username, String roleName, Set<String> features, Map<String, String> permissions) {
        Company company = new Company("Example AG");
        company.setEnabledFeatures(features);

        User user = new User();
        user.setUsername(username);
        user.setCompany(company);
        user.getRoles().add(new Role(roleName));
        user.setPagePermissions(permissions);
        return user;
    }

    private Map<String, String> allNonePagePermissionsFor(User user) {
        Map<String, String> permissions = new LinkedHashMap<>();
        userPermissionService.buildDefaultPagePermissions(user)
                .forEach((pageKey, ignored) -> permissions.put(pageKey, UserPermissionService.ACCESS_NONE));
        return permissions;
    }
}
