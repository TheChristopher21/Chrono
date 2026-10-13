package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsAccessDtos;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.*;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsPropertyAccessServiceTest {
    UserRepository users = mock(UserRepository.class);
    HotelPropertyRepository properties = mock(HotelPropertyRepository.class);
    PmsPropertyGrantRepository grants = mock(PmsPropertyGrantRepository.class);
    PmsAuditWriter audit = mock(PmsAuditWriter.class);
    PmsPropertyAccessService service = new PmsPropertyAccessService(users, properties, grants, new UserPermissionService(), audit);
    Company company;
    User staff;
    HotelProperty property;

    @BeforeEach void setup() {
        company = new Company("Hotel Group"); company.setId(10L); company.setEnabledFeatures(Set.of("pms"));
        staff = user(7L, "staff", false);
        property = new HotelProperty(); org.springframework.test.util.ReflectionTestUtils.setField(property, "id", 5L); property.setName("Hotel A"); property.setCompany(company);
        when(users.findByUsernameWithPermissionContext("staff")).thenReturn(Optional.of(staff));
    }
    @Test void unassignedStaffHaveNoImplicitAccessToChainHotels() {
        var access = service.access("staff");
        assertThat(access.any()).isFalse();
        assertThatThrownBy(() -> service.require(access, 5L, "FRONT_DESK", false)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void grantsSeparateHotelsModulesAndReadFromWrite() {
        PmsPropertyGrant grant = grant(Map.of("HOUSEKEEPING", "MANAGE", "FRONT_DESK", "VIEW"));
        when(grants.findByUser_IdAndProperty_Company_Id(7L, 10L)).thenReturn(List.of(grant));
        var access = service.access("staff");
        assertThat(access.allows(5L, "HOUSEKEEPING", true)).isTrue();
        assertThat(access.allows(5L, "FRONT_DESK", false)).isTrue();
        assertThat(access.allows(5L, "FRONT_DESK", true)).isFalse();
        assertThat(access.allows(5L, "FINANCE", false)).isFalse();
        assertThat(access.allows(6L, "HOUSEKEEPING", true)).isFalse();
    }
    @Test void pageViewPermissionCapsAnOtherwiseManageGrant() {
        staff.setPagePermissions(Map.of("pms", "VIEW"));
        when(grants.findByUser_IdAndProperty_Company_Id(7L, 10L)).thenReturn(List.of(grant(Map.of("FINANCE", "MANAGE"))));
        var access = service.access("staff");
        assertThat(access.allows(5L, "FINANCE", false)).isTrue();
        assertThat(access.allows(5L, "FINANCE", true)).isFalse();
    }
    @Test void masterKeepsAllHotelsButStillCannotCrossTenantBoundary() {
        User master = user(1L, "master", true);
        when(users.findByUsernameWithPermissionContext("master")).thenReturn(Optional.of(master));
        var access = service.access("master");
        assertThat(access.master()).isTrue();
        assertThatThrownBy(() -> service.require(access, 999L, "FINANCE", true)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void nonMasterCannotAssignTheirOwnGrants() {
        assertThatThrownBy(() -> service.update("staff", 7L, new PmsAccessDtos.Update(List.of())))
                .isInstanceOf(ResponseStatusException.class);
        verify(grants, never()).deleteByUser_Id(any());
    }
    @Test void masterCannotAssignForeignHotelAndValidationPrecedesDeletion() {
        User master = user(1L, "master", true);
        when(users.findByUsernameWithPermissionContext("master")).thenReturn(Optional.of(master));
        when(users.findById(7L)).thenReturn(Optional.of(staff));
        assertThatThrownBy(() -> service.update("master", 7L, new PmsAccessDtos.Update(List.of(
                new PmsAccessDtos.Grant(999L, Map.of("FINANCE", "MANAGE")))))).isInstanceOf(ResponseStatusException.class);
        verify(grants, never()).deleteByUser_Id(any());
    }
    @Test void permissionReplacementIsAuditedForRemovedAndNewHotel() {
        User master = user(1L, "master", true);
        when(users.findByUsernameWithPermissionContext("master")).thenReturn(Optional.of(master));
        when(users.findById(7L)).thenReturn(Optional.of(staff));
        when(properties.findByIdAndCompany_Id(5L, 10L)).thenReturn(Optional.of(property));
        when(grants.findByUser_IdAndProperty_Company_Id(7L, 10L)).thenReturn(List.of(grant(Map.of("FINANCE", "MANAGE"))));
        service.update("master", 7L, new PmsAccessDtos.Update(List.of()));
        verify(grants).deleteByUser_Id(7L);
        verify(audit).append(eq(property), eq("pms_access.changed"), eq("user"), eq("7"), contains("\"permissions\":{}"));
    }
    private User user(Long id, String name, boolean master) {
        User user = new User(); user.setId(id); user.setUsername(name); user.setCompany(company);
        user.getRoles().add(new Role(master ? "ROLE_ADMIN" : "ROLE_USER"));
        user.setPagePermissions(Map.of("pms", "MANAGE")); return user;
    }
    private PmsPropertyGrant grant(Map<String, String> values) {
        PmsPropertyGrant grant = new PmsPropertyGrant(); grant.setUser(staff); grant.setProperty(property); grant.setPermissions(values); return grant;
    }
}
