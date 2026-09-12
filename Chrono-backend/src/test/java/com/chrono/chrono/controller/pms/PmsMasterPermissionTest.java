package com.chrono.chrono.controller.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsSetupService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsMasterPermissionTest {
    private final UserPermissionService permissions = new UserPermissionService();

    private User user(String role) {
        Company company = new Company("Hotelgruppe");
        company.setId(1L);
        company.setEnabledFeatures(Set.of("pms"));
        User user = new User();
        user.setCompany(company);
        user.setRoles(Set.of(new Role(role)));
        user.setPagePermissions(Map.of("pms", "MANAGE"));
        return user;
    }

    @Test void receptionCannotGainMasterThroughRawPermissionMap() {
        User receptionist = user("ROLE_USER");
        receptionist.setPagePermissions(Map.of("pms", "MANAGE", "pmsSettings", "MANAGE"));
        assertThat(permissions.hasPageAccess(receptionist, "pms", "MANAGE")).isTrue();
        assertThat(permissions.hasPageAccess(receptionist, "pmsSettings", "MANAGE")).isFalse();
    }

    @Test void adminReceivesMasterButAnExplicitRevocationIsRespected() {
        User master = user("ROLE_ADMIN");
        assertThat(permissions.hasPageAccess(master, "pmsSettings", "MANAGE")).isTrue();
        master.setPagePermissions(Map.of("pms", "MANAGE", "pmsSettings", "NONE"));
        assertThat(permissions.hasPageAccess(master, "pmsSettings", "MANAGE")).isFalse();
    }

    @Test void setupControllerRejectsReceptionBeforeCallingService() {
        UserRepository users = mock(UserRepository.class);
        PmsSetupService setup = mock(PmsSetupService.class);
        when(users.findByUsernameWithPermissionContext("reception")).thenReturn(Optional.of(user("ROLE_USER")));
        PmsSetupController controller = new PmsSetupController(setup, users, permissions);
        assertThatThrownBy(() -> controller.createProperty(null, () -> "reception"))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("PMS-Master");
        verifyNoInteractions(setup);
    }

    @Test void setupControllerAllowsMasterForOwnCompany() {
        UserRepository users = mock(UserRepository.class);
        PmsSetupService setup = mock(PmsSetupService.class);
        User master = user("ROLE_ADMIN");
        when(users.findByUsernameWithPermissionContext("master")).thenReturn(Optional.of(master));
        new PmsSetupController(setup, users, permissions).createProperty(null, () -> "master");
        verify(setup).createProperty(master.getCompany(), null);
    }
}
