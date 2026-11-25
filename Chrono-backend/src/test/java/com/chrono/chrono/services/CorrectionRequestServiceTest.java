package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrectionRequestServiceTest {

    @Mock private CorrectionRequestRepository correctionRepo;
    @Mock private UserRepository userRepo;
    @Mock private TimeTrackingEntryRepository timeTrackingEntryRepo;
    @Mock private TimeTrackingService timeTrackingService;
    @Mock private AccessControlService accessControlService;
    @Mock private EntityManager entityManager;

    @InjectMocks private CorrectionRequestService service;

    @Test
    void creationKeepsTheOriginalStampEvenWhenTheLiveEntryChanges() {
        User employee = employee(10L, "employee");
        LocalDate day = LocalDate.of(2026, 10, 6);
        TimeTrackingEntry old = new TimeTrackingEntry(employee, day.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        old.setId(51L);
        when(userRepo.findByUsername("employee")).thenReturn(Optional.of(employee));
        when(timeTrackingEntryRepo.findById(51L)).thenReturn(Optional.of(old));
        when(timeTrackingEntryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(employee, day.atStartOfDay(), day.plusDays(1).atStartOfDay())).thenReturn(List.of(old));
        when(correctionRepo.save(any(CorrectionRequest.class))).thenAnswer(call -> call.getArgument(0));

        CorrectionRequest request = service.createCorrectionRequest("employee", 51L, day.atTime(18, 0), "ENDE", "Falsches Ende", day);
        old.setEntryTimestamp(day.atTime(17, 30));
        assertEquals(day.atTime(17, 0), request.getOriginalTimestamp());
        assertEquals(day.atTime(17, 0), request.getOriginalDayEntries().get(0).getEntryTimestamp());
        assertEquals(51L, request.getTargetEntryId());
        assertNull(request.getTargetEntry());
    }

    @Test
    void requestsWithoutAnIdKeepTheRealPreviousDayWithoutGuessingAPairing() {
        User employee = employee(10L, "employee");
        LocalDate day = LocalDate.of(2026, 10, 6);
        TimeTrackingEntry old = new TimeTrackingEntry(employee, day.atTime(8, 0), TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        when(userRepo.findByUsername("employee")).thenReturn(Optional.of(employee));
        when(timeTrackingEntryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(employee, day.atStartOfDay(), day.plusDays(1).atStartOfDay())).thenReturn(List.of(old));
        when(correctionRepo.save(any(CorrectionRequest.class))).thenAnswer(call -> call.getArgument(0));
        CorrectionRequest request = service.createCorrectionRequest("employee", null, day.atTime(17, 0), "ENDE", "Ende vergessen", day);
        assertNull(request.getOriginalTimestamp());
        assertEquals(day.atTime(8, 0), request.getOriginalDayEntries().get(0).getEntryTimestamp());
    }

    @Test
    void legacyPendingRequestsShowTheCurrentDayButProcessedRequestsNeverUseNewTimesAsOldTimes() {
        User employee = employee(10L, "employee");
        LocalDate day = LocalDate.of(2026, 10, 6);
        CorrectionRequest request = new CorrectionRequest(employee, day, day.atTime(18, 0), TimeTrackingEntry.PunchType.ENDE, "Korrektur");
        TimeTrackingEntry old = new TimeTrackingEntry(employee, day.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        when(correctionRepo.findAllWithDetails()).thenReturn(List.of(request));
        when(timeTrackingEntryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(employee, day.atStartOfDay(), day.plusDays(1).atStartOfDay())).thenReturn(List.of(old));
        assertEquals(day.atTime(17, 0), service.getAllRequestsForAdminDashboard().get(0).getOriginalDayEntries().get(0).getEntryTimestamp());
        request.setApproved(true);
        assertTrue(service.getAllRequestsForAdminDashboard().get(0).getOriginalDayEntries().isEmpty());
    }

    @Test
    void anExplicitStampMustExistAndBelongToTheEmployeeAndDay() {
        User employee = employee(10L, "employee");
        LocalDate day = LocalDate.of(2026, 10, 6);
        when(userRepo.findByUsername("employee")).thenReturn(Optional.of(employee));
        assertThrows(IllegalArgumentException.class, () -> service.createCorrectionRequest("employee", 9L, day.atTime(18, 0), "ENDE", "Korrektur", day));
        TimeTrackingEntry foreign = new TimeTrackingEntry(employee(20L, "other"), day.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        when(timeTrackingEntryRepo.findById(9L)).thenReturn(Optional.of(foreign));
        assertThrows(IllegalArgumentException.class, () -> service.createCorrectionRequest("employee", 9L, day.atTime(18, 0), "ENDE", "Korrektur", day));
        foreign.setUser(employee);
        foreign.setEntryTimestamp(day.minusDays(1).atTime(17, 0));
        assertThrows(IllegalArgumentException.class, () -> service.createCorrectionRequest("employee", 9L, day.atTime(18, 0), "ENDE", "Korrektur", day));
    }

    private User employee(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    void approvalStoresActingAdminOnRequestAndCreatedEntries() {
        User employee = new User();
        employee.setId(10L);
        employee.setUsername("employee");

        User admin = new User();
        admin.setUsername("anna.berger");
        admin.setFirstName("Anna");
        admin.setLastName("Berger");

        LocalDate correctionDate = LocalDate.of(2026, 8, 20);
        LocalDateTime desiredTime = correctionDate.atTime(8, 0);
        CorrectionRequest request = new CorrectionRequest(
                employee,
                correctionDate,
                desiredTime,
                TimeTrackingEntry.PunchType.START,
                "Stempel vergessen"
        );
        request.setId(42L);

        when(correctionRepo.findById(42L)).thenReturn(Optional.of(request));
        when(userRepo.findByUsername("anna.berger")).thenReturn(Optional.of(admin));
        when(accessControlService.isSuperAdmin(admin)).thenReturn(true);
        when(userRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(employee));
        when(correctionRepo.findByUserAndDesiredTimestampBetweenAndApprovedIsFalseAndDeniedIsFalse(
                any(User.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(request));
        when(timeTrackingEntryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(
                any(User.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(correctionRepo.save(any(CorrectionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveRequest(42L, "Passt", "anna.berger");

        ArgumentCaptor<TimeTrackingEntry> entryCaptor = ArgumentCaptor.forClass(TimeTrackingEntry.class);
        verify(timeTrackingEntryRepo).save(entryCaptor.capture());
        TimeTrackingEntry createdEntry = entryCaptor.getValue();
        assertEquals("anna.berger", createdEntry.getCorrectionAdminUsername());
        assertEquals("AB", createdEntry.getCorrectionAdminInitials());
        assertEquals("anna.berger", request.getProcessedByAdminUsername());
        assertEquals("AB", request.getProcessedByAdminInitials());
        assertTrue(request.isApproved());
    }
}
