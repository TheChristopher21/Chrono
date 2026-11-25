package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NfcAgentAuthServiceTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private NfcAgentAuthService service;

    @BeforeEach
    void setUp() {
        service = new NfcAgentAuthService(new ObjectMapper(), environment, companies);
        ReflectionTestUtils.setField(service, "agentToken", "existing-live-token");
        ReflectionTestUtils.setField(service, "registryJson", "[]");
        when(companies.existsById(17L)).thenReturn(true);
        when(companies.existsById(29L)).thenReturn(true);
    }

    @Test
    void existingTokenNeedsExplicitOwnershipAndNeverGuessesACompany() {
        assertThrows(AccessDeniedException.class, () -> service.requireAgent("existing-live-token", null, null));
        assertThrows(ResponseStatusException.class, () -> service.routeForCompany(17L));
        verify(companies, never()).findAll();
    }

    @Test
    void existingTokenAndHeadersWorkAfterExplicitPraxisOwnershipIsConfigured() {
        legacyOwnership();
        var agent = service.requireAgent("existing-live-token", "true", new MockHttpServletRequest());
        assertEquals(new NfcAgentAuthService.AgentIdentity(17L, "praxis-reader"), agent);
        assertEquals(agent, service.routeForCompany(17L));
        assertDoesNotThrow(() -> service.requireLocalHardware(agent));
        assertThrows(AccessDeniedException.class, () -> service.requireAgent("wrong-token", null, null));
        assertThrows(ResponseStatusException.class, () -> service.routeForCompany(29L));
    }

    @Test
    void localhostHeaderAlsoRequiresExplicitCompanyAndDevice() {
        ReflectionTestUtils.setField(service, "allowLocalhostLegacy", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        assertThrows(AccessDeniedException.class, () -> service.requireAgent(null, "true", request));
        legacyOwnership();
        assertEquals(17L, service.requireAgent(null, "true", request).companyId());
        request.setRemoteAddr("192.0.2.3");
        assertThrows(AccessDeniedException.class, () -> service.requireAgent(null, "true", request));
    }

    @Test
    void configuredAgentsHaveSeparateCompaniesDevicesAndHardwarePermissions() {
        environment.withProperty("FIRST_NFC_SECRET", "first-token").withProperty("SECOND_NFC_SECRET", "second-token");
        ReflectionTestUtils.setField(service, "registryJson", """
                [{"companyId":17,"deviceId":"reader-a","tokenEnv":"FIRST_NFC_SECRET","localHardware":true},
                 {"companyId":29,"deviceId":"reader-b","tokenEnv":"SECOND_NFC_SECRET"}]
                """);
        var first = service.requireAgent("first-token", null, null);
        var second = service.requireAgent("second-token", null, null);
        assertEquals(first, service.routeForCompany(17L));
        assertEquals(second, service.routeForCompany(29L));
        assertDoesNotThrow(() -> service.requireLocalHardware(first));
        assertThrows(AccessDeniedException.class, () -> service.requireLocalHardware(second));
        Company other = new Company("Other company"); other.setId(29L);
        User user = new User(); user.setCompany(other);
        assertThrows(AccessDeniedException.class, () -> service.requireUserInCompany(first, user));
        assertDoesNotThrow(() -> service.requireUserInCompany(second, user));
    }

    @Test
    void duplicateTokenCannotAuthenticateAmbiguousTenantsAndErrorsDoNotExposeIt() {
        environment.withProperty("SHARED_SECRET", "secret-must-not-appear-in-errors");
        ReflectionTestUtils.setField(service, "registryJson", """
                [{"companyId":17,"deviceId":"reader-a","tokenEnv":"SHARED_SECRET"},
                 {"companyId":29,"deviceId":"reader-b","tokenEnv":"SHARED_SECRET"}]
                """);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.requireAgent("secret-must-not-appear-in-errors", null, null));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getMessage().contains("secret-must-not-appear-in-errors"));
    }

    @Test
    void twoDevicesInOneCompanyNeedAnExplicitDefaultForEnqueue() {
        environment.withProperty("FIRST_SECRET", "first").withProperty("SECOND_SECRET", "second");
        ReflectionTestUtils.setField(service, "registryJson", """
                [{"companyId":17,"deviceId":"reader-a","tokenEnv":"FIRST_SECRET"},
                 {"companyId":17,"deviceId":"reader-b","tokenEnv":"SECOND_SECRET"}]
                """);
        assertThrows(ResponseStatusException.class, () -> service.routeForCompany(17L));
        assertEquals("reader-a", service.requireAgent("first", null, null).deviceId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"17.8", "17.0", "1.7e1", "9223372036854775808", "\"17\"", "0", "-1"})
    void registryNeverTruncatesOrReinterpretsAConfiguredCompanyId(String jsonId) {
        environment.withProperty("FIRST_SECRET", "first-token");
        ReflectionTestUtils.setField(service, "registryJson",
                "[{\"companyId\":" + jsonId + ",\"deviceId\":\"reader\",\"tokenEnv\":\"FIRST_SECRET\"}]");
        var error = assertThrows(ResponseStatusException.class, () -> service.requireAgent("first-token", null, null));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(companies);
    }

    private void legacyOwnership() {
        ReflectionTestUtils.setField(service, "legacyCompanyId", "17");
        ReflectionTestUtils.setField(service, "legacyDeviceId", "praxis-reader");
    }
}
