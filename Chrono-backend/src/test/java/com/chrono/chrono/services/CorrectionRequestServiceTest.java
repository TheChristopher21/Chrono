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
