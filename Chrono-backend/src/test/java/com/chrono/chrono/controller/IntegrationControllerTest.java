package com.chrono.chrono.controller;

import com.chrono.chrono.dto.IntegrationConfigDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.IntegrationConfig;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.IntegrationConfigService;
import com.chrono.chrono.services.ReportService;
import com.chrono.chrono.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationControllerTest {

    @Mock private IntegrationConfigService integrationConfigService;
    @Mock private UserService userService;
    @Mock private ReportService reportService;
    @Mock private ComplianceAuditService complianceAuditService;

    private IntegrationController controller;

    @BeforeEach
    void setUp() {
        controller = new IntegrationController();
        ReflectionTestUtils.setField(controller, "integrationConfigService", integrationConfigService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "reportService", reportService);
        ReflectionTestUtils.setField(controller, "complianceAuditService", complianceAuditService);
    }

    @Test
    void listsOnlyCurrentCompanyIntegrationsForModernProjectsFeature() {
        Company company = company(21L, false, Set.of("projects"));
        User user = user("admin", company, "ROLE_ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(user);
        when(integrationConfigService.findByCompanyId(21L)).thenReturn(List.of());

        var response = controller.listIntegrations(() -> "admin");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(), response.getBody());
        verify(integrationConfigService).findByCompanyId(21L);
    }

    @Test
    void rejectsUnscopedSuperAdminInsteadOfReturningCrossTenantData() {
        User user = user("root", null, "ROLE_SUPERADMIN");
        when(userService.getUserByUsername("root")).thenReturn(user);

        var response = controller.listIntegrations(() -> "root");

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(integrationConfigService);
    }

    @Test
    void rejectsUpdatesToAnotherCompanyIntegration() {
        Company actorCompany = company(21L, false, Set.of("projects"));
        Company foreignCompany = company(22L, true, Set.of());
        User user = user("admin", actorCompany, "ROLE_ADMIN");
        IntegrationConfig foreignConfig = new IntegrationConfig();
        foreignConfig.setCompany(foreignCompany);
        when(userService.getUserByUsername("admin")).thenReturn(user);
        when(integrationConfigService.findById(7L)).thenReturn(Optional.of(foreignConfig));

        var response = controller.updateIntegration(7L, new IntegrationConfigDTO(), () -> "admin");

        assertEquals(403, response.getStatusCode().value());
        verify(integrationConfigService, never()).save(foreignConfig);
        verifyNoInteractions(complianceAuditService);
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
