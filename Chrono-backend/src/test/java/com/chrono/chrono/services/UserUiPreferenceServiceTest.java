package com.chrono.chrono.services;

import com.chrono.chrono.dto.UiPreferenceResponse;
import com.chrono.chrono.dto.UiPreferenceUpdateRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserUiPreference;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.exceptions.UiPreferenceRevisionConflictException;
import com.chrono.chrono.repositories.UserUiPreferenceRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUiPreferenceServiceTest {

    @Mock
    private UserUiPreferenceRepository preferenceRepository;
    @Mock
    private HotelPropertyRepository propertyRepository;
    @Mock
    private AccessControlService accessControlService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal principal = () -> "alice";
    private UserUiPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new UserUiPreferenceService(
                preferenceRepository,
                propertyRepository,
                accessControlService,
                new UserPermissionService(),
                new UiPreferencePayloadValidator(objectMapper)
        );
    }

    @Test
    void getReturnsRevisionZeroDefaultWithoutPersisting() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_STANDARD"))
                .thenReturn(Optional.empty());

        UiPreferenceResponse response = service.get(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null
        );

        assertEquals(0L, response.revision());
        assertEquals(1, response.schemaVersion());
        assertTrue(response.payload().isObject());
        assertTrue(response.payload().isEmpty());
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void deletedAuthenticatedUserIsDeniedBeforePreferenceLookup() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        actor.setDeleted(true);
        authenticate(actor);

        assertThrows(AccessDeniedException.class, () -> service.get(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null
        ));

        verify(preferenceRepository, never())
                .findByUser_IdAndTenantKeyAndAreaAndContextKey(any(), any(), any(), any());
    }

    @Test
    void putCreatesSelfScopedCompanyPreference() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        ObjectNode payload = dashboardPayload("default", "weekly-summary");
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_HOURLY"))
                .thenReturn(Optional.empty());
        when(preferenceRepository.saveAndFlush(any(UserUiPreference.class))).thenAnswer(invocation -> {
            UserUiPreference preference = invocation.getArgument(0);
            preference.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
            return preference;
        });

        UiPreferenceResponse response = service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_HOURLY",
                null,
                new UiPreferenceUpdateRequest(1, 0L, payload)
        );

        ArgumentCaptor<UserUiPreference> captor = ArgumentCaptor.forClass(UserUiPreference.class);
        verify(preferenceRepository).saveAndFlush(captor.capture());
        UserUiPreference stored = captor.getValue();
        assertEquals(actor, stored.getUser());
        assertEquals(actor.getCompany(), stored.getCompany());
        assertEquals("company:10", stored.getTenantKey());
        assertEquals("USER_HOURLY", stored.getContextKey());
        assertEquals(0L, response.revision());
        assertEquals("weekly-summary", response.payload()
                .path("layouts").path("default").path("widgets").path(0).path("id").asText());
    }

    @Test
    void putRejectsStaleRevisionWithoutMutation() throws Exception {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        UserUiPreference existing = preference(
                actor,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                dashboardPayload("default", "old-widget"),
                5L,
                null
        );
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_STANDARD"))
                .thenReturn(Optional.of(existing));

        assertThrows(UiPreferenceRevisionConflictException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(1, 4L, dashboardPayload("default", "new-widget"))
        ));

        assertEquals("old-widget", objectMapper.readTree(existing.getPayload())
                .path("layouts").path("default").path("widgets").path(0).path("id").asText());
        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void putReturnsPostFlushOptimisticRevision() throws Exception {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        UserUiPreference existing = preference(
                actor,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                dashboardPayload("default", "old-widget"),
                2L,
                null
        );
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_STANDARD"))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.saveAndFlush(existing)).thenAnswer(invocation -> {
            existing.setRevision(3L);
            return existing;
        });

        UiPreferenceResponse response = service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(1, 2L, dashboardPayload("default", "new-widget"))
        );

        assertEquals(3L, response.revision());
    }

    @Test
    void pmsPreferenceRequiresFeatureAndTenantOwnedProperty() {
        Company company = company(10L, Set.of("pms"));
        User actor = user(
                1L,
                "alice",
                company,
                "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW)
        );
        authenticate(actor);
        HotelProperty property = org.mockito.Mockito.mock(HotelProperty.class);
        when(propertyRepository.findByIdAndCompany_Id(42L, 10L)).thenReturn(Optional.of(property));
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.PMS_DASHBOARD, "property:42"))
                .thenReturn(Optional.empty());
        when(preferenceRepository.saveAndFlush(any(UserUiPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UiPreferenceResponse response = service.put(
                principal,
                UserUiPreferenceArea.PMS_DASHBOARD,
                null,
                42L,
                new UiPreferenceUpdateRequest(1, 0L, dashboardPayload("property:42", "occupancy"))
        );

        assertEquals("property:42", response.context());
        ArgumentCaptor<UserUiPreference> captor = ArgumentCaptor.forClass(UserUiPreference.class);
        verify(preferenceRepository).saveAndFlush(captor.capture());
        assertEquals(property, captor.getValue().getProperty());
    }

    @Test
    void savesAndRestoresPmsGridCoordinatesExactlyWithVersionOneAndOptimisticRevision() throws Exception {
        Company company = company(10L, Set.of("pms"));
        User actor = user(1L, "alice", company, "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW));
        authenticate(actor);
        HotelProperty property = new HotelProperty();
        org.springframework.test.util.ReflectionTestUtils.setField(property, "id", 42L);
        property.setCompany(company);
        when(propertyRepository.findByIdAndCompany_Id(42L, 10L)).thenReturn(Optional.of(property));
        UserUiPreference existing = preference(actor, UserUiPreferenceArea.PMS_DASHBOARD,
                "property:42", dashboardPayload("property:42", "occupancy"), 4L, property);
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.PMS_DASHBOARD, "property:42"))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.saveAndFlush(existing)).thenAnswer(invocation -> {
            existing.setRevision(5L);
            return existing;
        });
        ObjectNode payload = dashboardPayload("property:42", "occupancy");
        ((ObjectNode) payload.path("layouts").path("property:42").path("widgets").path(0))
                .put("x", 7).put("y", 176).put("w", 5).put("h", 24);

        UiPreferenceResponse saved = service.put(principal, UserUiPreferenceArea.PMS_DASHBOARD,
                null, 42L, new UiPreferenceUpdateRequest(1, 4L, payload));
        UiPreferenceResponse restored = service.get(principal, UserUiPreferenceArea.PMS_DASHBOARD, null, 42L);

        assertEquals(payload, objectMapper.readTree(existing.getPayload()));
        assertEquals(payload, saved.payload());
        assertEquals(payload, restored.payload());
        assertEquals(1, restored.schemaVersion());
        assertEquals(5L, restored.revision());
        assertEquals("property:42", restored.context());
    }

    @Test
    void pmsPreferenceRejectsStalePermissionWhenCompanyFeatureIsDisabled() {
        User actor = user(
                1L,
                "alice",
                company(10L, Set.of()),
                "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW)
        );
        authenticate(actor);

        assertThrows(AccessDeniedException.class, () -> service.get(
                principal, UserUiPreferenceArea.PMS_DASHBOARD, null, 42L));

        verify(propertyRepository, never()).findByIdAndCompany_Id(any(), any());
    }

    @Test
    void pmsPreferenceRejectsUserWithoutCompanyBeforePropertyLookup() {
        User actor = user(
                1L,
                "alice",
                null,
                "ROLE_SUPERADMIN",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE)
        );
        authenticate(actor);

        assertThrows(AccessDeniedException.class, () -> service.get(
                principal, UserUiPreferenceArea.PMS_DASHBOARD, null, 42L));

        verify(propertyRepository, never()).findByIdAndCompany_Id(any(), any());
        verify(preferenceRepository, never())
                .findByUser_IdAndTenantKeyAndAreaAndContextKey(any(), any(), any(), any());
    }

    @Test
    void pmsPreferenceHidesForeignAndMissingPropertiesBehindSameDenial() {
        User actor = user(
                1L,
                "alice",
                company(10L, Set.of("pms")),
                "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW)
        );
        authenticate(actor);
        when(propertyRepository.findByIdAndCompany_Id(99L, 10L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.get(
                principal, UserUiPreferenceArea.PMS_DASHBOARD, null, 99L));

        verify(preferenceRepository, never())
                .findByUser_IdAndTenantKeyAndAreaAndContextKey(any(), any(), any(), any());
    }

    @Test
    void appTabsRejectUnknownOrUnauthorizedViews() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        ObjectNode payload = appTabs("admin", UserPermissionService.PAGE_ADMIN_DASHBOARD, null);

        assertThrows(AccessDeniedException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.APP_TABS,
                "workspace",
                null,
                new UiPreferenceUpdateRequest(1, 0L, payload)
        ));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void appTabsRejectPmsPropertyFromAnotherTenant() {
        User actor = user(
                1L,
                "alice",
                company(10L, Set.of("pms")),
                "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW)
        );
        authenticate(actor);
        ObjectNode payload = appTabs("pms", UserPermissionService.PAGE_PMS, 77L);
        when(propertyRepository.findByIdAndCompany_Id(77L, 10L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.APP_TABS,
                "workspace",
                null,
                new UiPreferenceUpdateRequest(1, 0L, payload)
        ));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void getFiltersTabsWhosePermissionWasRevokedAndRepairsActiveTab() throws Exception {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tabs = payload.putArray("tabs");
        tabs.add(tab("dashboard", UserPermissionService.PAGE_DASHBOARD, null));
        tabs.add(tab("admin", UserPermissionService.PAGE_ADMIN_DASHBOARD, null));
        payload.put("activeTabId", "admin");
        UserUiPreference stored = preference(
                actor,
                UserUiPreferenceArea.APP_TABS,
                "workspace",
                payload,
                3L,
                null
        );
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.APP_TABS, "workspace"))
                .thenReturn(Optional.of(stored));

        UiPreferenceResponse response = service.get(
                principal, UserUiPreferenceArea.APP_TABS, "workspace", null);

        assertEquals(1, response.payload().path("tabs").size());
        assertEquals("dashboard", response.payload().path("activeTabId").asText());
    }

    @Test
    void restoresExplicitCopiesWithoutRestoringPmsTabsForForeignProperties() throws Exception {
        User actor = user(1L, "alice", company(10L, Set.of("pms")), "ROLE_USER",
                Map.of(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW));
        authenticate(actor);
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tabs = payload.putArray("tabs");
        tabs.add(tab("chrono-original", UserPermissionService.PAGE_DASHBOARD, null));
        tabs.add(tab("chrono-copy", UserPermissionService.PAGE_DASHBOARD, null));
        tabs.add(tab("pms-original", UserPermissionService.PAGE_PMS, 42L));
        tabs.add(tab("pms-copy", UserPermissionService.PAGE_PMS, 42L));
        tabs.add(tab("pms-foreign-copy", UserPermissionService.PAGE_PMS, 77L));
        payload.put("activeTabId", "pms-copy");
        UserUiPreference stored = preference(actor, UserUiPreferenceArea.APP_TABS,
                "workspace", payload, 3L, null);
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.APP_TABS, "workspace"))
                .thenReturn(Optional.of(stored));
        when(propertyRepository.findByIdAndCompany_Id(42L, 10L))
                .thenReturn(Optional.of(new HotelProperty()));
        when(propertyRepository.findByIdAndCompany_Id(77L, 10L)).thenReturn(Optional.empty());

        UiPreferenceResponse response = service.get(
                principal, UserUiPreferenceArea.APP_TABS, "workspace", null);

        assertEquals(4, response.payload().path("tabs").size());
        assertEquals("chrono-original", response.payload().path("tabs").path(0).path("id").asText());
        assertEquals("chrono-copy", response.payload().path("tabs").path(1).path("id").asText());
        assertEquals("pms-original", response.payload().path("tabs").path(2).path("id").asText());
        assertEquals("pms-copy", response.payload().path("tabs").path(3).path("id").asText());
        assertEquals("pms-copy", response.payload().path("activeTabId").asText());
        assertEquals(5, objectMapper.readTree(stored.getPayload()).path("tabs").size());
    }

    @Test
    void rejectsInvalidDeepAndOversizedPayloadsBeforePersistence() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);

        assertThrows(IllegalArgumentException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(1, 0L, objectMapper.createArrayNode())
        ));

        ObjectNode deep = objectMapper.createObjectNode();
        ObjectNode cursor = deep;
        for (int i = 0; i < 14; i++) {
            ObjectNode child = objectMapper.createObjectNode();
            cursor.set("child", child);
            cursor = child;
        }
        assertThrows(IllegalArgumentException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(1, 0L, deep)
        ));

        ObjectNode oversized = objectMapper.createObjectNode();
        String value = "x".repeat(1_000);
        for (int i = 0; i < 70; i++) {
            oversized.put("field" + i, value);
        }
        assertThrows(IllegalArgumentException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(1, 0L, oversized)
        ));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRejectsStaleRevisionAndPreservesRow() throws Exception {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);
        UserUiPreference existing = preference(
                actor,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                objectMapper.createObjectNode(),
                7L,
                null
        );
        when(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                1L, "company:10", UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_STANDARD"))
                .thenReturn(Optional.of(existing));

        assertThrows(UiPreferenceRevisionConflictException.class, () -> service.delete(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                6L
        ));

        verify(preferenceRepository, never()).delete(any());
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidContext() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_USER", Map.of());
        authenticate(actor);

        assertThrows(IllegalArgumentException.class, () -> service.put(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null,
                new UiPreferenceUpdateRequest(2, 0L, objectMapper.createObjectNode())
        ));
        assertThrows(IllegalArgumentException.class, () -> service.get(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "contains spaces",
                null
        ));
    }

    @Test
    void rejectsClientInventedContextsBeforePreferenceLookup() {
        User actor = user(1L, "alice", company(10L, Set.of()), "ROLE_SUPERADMIN", Map.of());
        authenticate(actor);

        assertThrows(IllegalArgumentException.class, () -> service.get(
                principal, UserUiPreferenceArea.APP_TABS, "second-workspace", null));
        assertThrows(IllegalArgumentException.class, () -> service.get(
                principal, UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_CUSTOM", null));
        assertThrows(IllegalArgumentException.class, () -> service.get(
                principal, UserUiPreferenceArea.TIME_ADMIN_DASHBOARD, "ADMIN_CUSTOM", null));

        verify(preferenceRepository, never())
                .findByUser_IdAndTenantKeyAndAreaAndContextKey(any(), any(), any(), any());
    }

    private void authenticate(User actor) {
        when(accessControlService.requireAuthenticatedUser(principal)).thenReturn(actor);
    }

    private UserUiPreference preference(
            User actor,
            UserUiPreferenceArea area,
            String context,
            JsonNode payload,
            long revision,
            HotelProperty property
    ) throws Exception {
        UserUiPreference preference = new UserUiPreference();
        preference.setId(100L);
        preference.setUser(actor);
        preference.setCompany(actor.getCompany());
        preference.setProperty(property);
        preference.setTenantKey(actor.getCompany() != null ? "company:" + actor.getCompany().getId() : "global");
        preference.setArea(area);
        preference.setContextKey(context);
        preference.setSchemaVersion(1);
        preference.setPayload(objectMapper.writeValueAsString(payload));
        preference.setRevision(revision);
        preference.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        return preference;
    }

    private ObjectNode appTabs(String id, String viewKey, Long propertyId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("tabs").add(tab(id, viewKey, propertyId));
        payload.put("activeTabId", id);
        return payload;
    }

    private ObjectNode dashboardPayload(String scope, String widgetId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "chrono-dashboard-layouts");
        payload.put("schemaVersion", 1);
        ObjectNode layout = payload.putObject("layouts").putObject(scope);
        ObjectNode widget = layout.putArray("widgets").addObject();
        widget.put("id", widgetId);
        widget.put("visible", true);
        widget.put("order", 0);
        widget.put("size", "M");
        return payload;
    }

    private ObjectNode tab(String id, String viewKey, Long propertyId) {
        ObjectNode tab = objectMapper.createObjectNode();
        tab.put("id", id);
        tab.put("viewKey", viewKey);
        tab.putObject("params");
        if (propertyId != null) {
            ((ObjectNode) tab.get("params")).put("propertyId", propertyId);
        }
        tab.put("pinned", false);
        return tab;
    }

    private Company company(Long id, Set<String> features) {
        Company company = new Company("Company " + id);
        company.setId(id);
        company.setEnabledFeatures(features);
        return company;
    }

    private User user(
            Long id,
            String username,
            Company company,
            String roleName,
            Map<String, String> permissions
    ) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setCompany(company);
        user.getRoles().add(new Role(roleName));
        user.setPagePermissions(permissions);
        return user;
    }
}
