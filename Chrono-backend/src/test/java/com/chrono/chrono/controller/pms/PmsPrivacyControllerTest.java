package com.chrono.chrono.controller.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsPrivacyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsPrivacyControllerTest {

    @Mock private PmsPrivacyService privacyService;
    @Mock private UserRepository userRepository;
    @Mock private UserPermissionService permissionService;

    @Test
    void exportGuest_acceptsRegularRoleAdminWithPmsManagementPermission() {
        Company company = new Company("Hotel Testbetrieb");
        company.setId(41L);

        User admin = new User();
        admin.setUsername("hotel-test-admin");
        admin.setCompany(company);
        admin.setRoles(Set.of(new Role("ROLE_ADMIN")));

        Principal principal = () -> "hotel-test-admin";
        when(userRepository.findByUsernameWithPermissionContext("hotel-test-admin"))
                .thenReturn(Optional.of(admin));

        PmsPrivacyController controller =
                new PmsPrivacyController(privacyService, userRepository, permissionService);

        ResponseEntity<?> response = controller.exportGuest(7L, principal);

        assertEquals(200, response.getStatusCode().value());
        verify(permissionService).assertPageAccess(
                admin,
                UserPermissionService.PAGE_PMS,
                UserPermissionService.ACCESS_MANAGE,
                "PMS-Verwaltungsrecht erforderlich."
        );
        verify(privacyService).exportGuestData(company, 7L, "hotel-test-admin");
    }
}
