package com.chrono.chrono.controller;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TimeTrackingNfcIsolationTest {
    @Test
    void existingPraxisPunchRequestWorksAndOtherCompanyCannotChangeAnyTimeEntry() {
        Company praxis = new Company("Praxis"); praxis.setId(17L);
        Company other = new Company("Other"); other.setId(29L);
        User existingUser = new User(); existingUser.setUsername("existing-user"); existingUser.setCompany(praxis);
        User foreignUser = new User(); foreignUser.setUsername("foreign-user"); foreignUser.setCompany(other);
        TimeTrackingEntry historical = new TimeTrackingEntry(existingUser, LocalDateTime.of(2026, 10, 5, 8, 0),
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.NFC_SCAN);
        historical.setId(5L);
        historical.setDurationMinutes(123);
        historical.setDescription("Historical entry must stay unchanged");
        TimeTrackingEntryRepository entries = mock(TimeTrackingEntryRepository.class);
        List<TimeTrackingEntry> stored = new ArrayList<>(List.of(historical));
        when(entries.findLastEntryByUserAndDate(eq(existingUser), any())).thenReturn(Optional.empty());
        when(entries.save(any())).thenAnswer(call -> {
            TimeTrackingEntry inserted = call.getArgument(0);
            assertNotSame(historical, inserted);
            inserted.setId(6L); stored.add(inserted); return inserted;
        });
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("existing-user")).thenReturn(Optional.of(existingUser));
        VacationRequestRepository vacations = mock(VacationRequestRepository.class);
        when(vacations.findByUserAndApprovedTrue(existingUser)).thenReturn(List.of());
        TimeTrackingService tracking = spy(new TimeTrackingService());
        ReflectionTestUtils.setField(tracking, "timeTrackingEntryRepository", entries);
        ReflectionTestUtils.setField(tracking, "userRepository", userRepository);
        ReflectionTestUtils.setField(tracking, "vacationRequestRepository", vacations);
        // Balance calculation is covered separately; exercise the existing punch persistence path here.
        doNothing().when(tracking).rebuildUserBalance(any());
        UserService users = mock(UserService.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        when(companies.existsById(17L)).thenReturn(true);
        NfcAgentAuthService agents = new NfcAgentAuthService(new ObjectMapper(), new MockEnvironment(), companies);
        ReflectionTestUtils.setField(agents, "agentToken", "unchanged-existing-live-token");
        ReflectionTestUtils.setField(agents, "legacyCompanyId", "17");
        ReflectionTestUtils.setField(agents, "legacyDeviceId", "praxis-reader");
        ReflectionTestUtils.setField(agents, "registryJson", "[]");
        when(users.getUserByUsername("existing-user")).thenReturn(existingUser);
        when(users.getUserByUsername("foreign-user")).thenReturn(foreignUser);
        User superadmin = new User(); superadmin.setUsername("superadmin");
        when(users.getUserByUsername("superadmin")).thenReturn(superadmin);
        TimeTrackingController controller = new TimeTrackingController();
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "timeTrackingService", tracking);
        ReflectionTestUtils.setField(controller, "nfcAgentAuthService", agents);
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Existing agent parameters/header convention are unchanged; no JWT is needed.
        var response = controller.punch("existing-user", null, null, null, null, null, null,
                "unchanged-existing-live-token", "true", null, null, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("existing-user", response.getBody().getUsername());
        assertThrows(AccessDeniedException.class, () -> controller.punch("foreign-user", null, null, null, null, null, null,
                "unchanged-existing-live-token", "true", null, null, request));
        assertThrows(AccessDeniedException.class, () -> controller.punch("foreign-user", null, null, null, null, null, () -> "superadmin",
                "unchanged-existing-live-token", "true", null, "NFC_SCAN", request));
        verify(tracking).handlePunch("existing-user", TimeTrackingEntry.PunchSource.NFC_SCAN, null, null, null, null, null);
        verify(tracking, never()).handlePunch(eq("foreign-user"), any(), any(), any(), any(), any(), any());
        verify(entries, times(1)).save(any());
        verify(entries, never()).delete(any());
        verify(entries, never()).deleteAll();
        assertEquals(2, stored.size());
        assertSame(historical, stored.get(0));
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 0), historical.getEntryTimestamp());
        assertEquals(123, historical.getDurationMinutes());
        assertEquals("Historical entry must stay unchanged", historical.getDescription());
        assertEquals(TimeTrackingEntry.PunchSource.NFC_SCAN, historical.getSource());
    }
}
