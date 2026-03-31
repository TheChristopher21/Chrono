package com.chrono.chrono.services;

import com.chrono.chrono.entities.EmploymentModelType;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserEmploymentModelHistory;
import com.chrono.chrono.repositories.UserEmploymentModelHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmploymentModelHistoryServiceTest {

    @Mock
    private UserEmploymentModelHistoryRepository historyRepository;

    private EmploymentModelHistoryService service;

    @BeforeEach
    void setUp() {
        service = new EmploymentModelHistoryService(historyRepository);
    }

    @Test
    void resolveModelForDate_usesEarliestHistoryWhenDateIsBeforeFirstEntry() {
        User user = new User();
        UserEmploymentModelHistory first = new UserEmploymentModelHistory();
        first.setModelType(EmploymentModelType.HOURLY);
        first.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        when(historyRepository.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(historyRepository.findFirstByUserOrderByEffectiveFromAsc(eq(user))).thenReturn(Optional.of(first));

        EmploymentModelType result = service.resolveModelForDate(user, LocalDate.of(2023, 12, 31));

        assertEquals(EmploymentModelType.HOURLY, result);
    }

    @Test
    void ensureBaselineEntry_createsEntryWhenFirstHistoryStartsTooLate() {
        User user = new User();
        UserEmploymentModelHistory first = new UserEmploymentModelHistory();
        first.setModelType(EmploymentModelType.STANDARD);
        first.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        when(historyRepository.findFirstByUserOrderByEffectiveFromAsc(eq(user))).thenReturn(Optional.of(first));

        service.ensureBaselineEntry(user, EmploymentModelType.HOURLY, LocalDate.of(2024, 1, 1));

        ArgumentCaptor<UserEmploymentModelHistory> captor = ArgumentCaptor.forClass(UserEmploymentModelHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(EmploymentModelType.HOURLY, captor.getValue().getModelType());
        assertEquals(LocalDate.of(2024, 1, 1), captor.getValue().getEffectiveFrom());
    }

    @Test
    void recordModelChange_savesWhenModelActuallyChanges() {
        User user = new User();
        UserEmploymentModelHistory previous = new UserEmploymentModelHistory();
        previous.setModelType(EmploymentModelType.HOURLY);
        previous.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(historyRepository.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(eq(user), any()))
                .thenReturn(Optional.of(previous));

        service.recordModelChange(user, EmploymentModelType.STANDARD, LocalDate.of(2026, 3, 1));

        ArgumentCaptor<UserEmploymentModelHistory> captor = ArgumentCaptor.forClass(UserEmploymentModelHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(EmploymentModelType.STANDARD, captor.getValue().getModelType());
        assertEquals(LocalDate.of(2026, 3, 1), captor.getValue().getEffectiveFrom());
    }

    @Test
    void recordModelChange_updatesExistingRowWhenSameEffectiveDateExists() {
        User user = new User();
        UserEmploymentModelHistory sameDay = new UserEmploymentModelHistory();
        sameDay.setModelType(EmploymentModelType.HOURLY);
        sameDay.setEffectiveFrom(LocalDate.of(2026, 3, 1));
        when(historyRepository.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(eq(user), any()))
                .thenReturn(Optional.of(sameDay));
        when(historyRepository.findByUserOrderByEffectiveFromAsc(eq(user))).thenReturn(java.util.List.of(sameDay));

        service.recordModelChange(user, EmploymentModelType.STANDARD, LocalDate.of(2026, 3, 1));

        ArgumentCaptor<UserEmploymentModelHistory> captor = ArgumentCaptor.forClass(UserEmploymentModelHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 3, 1), captor.getValue().getEffectiveFrom());
        assertEquals(EmploymentModelType.STANDARD, captor.getValue().getModelType());
    }

    @Test
    void recordSnapshotChange_writesRowForSameModel() {
        User user = new User();
        user.setIsPercentage(false);
        user.setIsHourly(false);
        user.setDailyWorkHours(8.0);
        when(historyRepository.findByUserOrderByEffectiveFromAsc(eq(user))).thenReturn(java.util.List.of());

        service.recordSnapshotChange(user, LocalDate.of(2026, 4, 1));

        ArgumentCaptor<UserEmploymentModelHistory> captor = ArgumentCaptor.forClass(UserEmploymentModelHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(EmploymentModelType.STANDARD, captor.getValue().getModelType());
        assertEquals(LocalDate.of(2026, 4, 1), captor.getValue().getEffectiveFrom());
        assertEquals(8.0, captor.getValue().getDailyWorkHours(), 0.001);
    }

    @Test
    void needsBaselineBefore_returnsTrueWhenEarliestHistoryStartsTooLate() {
        User user = new User();
        UserEmploymentModelHistory first = new UserEmploymentModelHistory();
        first.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        when(historyRepository.findFirstByUserOrderByEffectiveFromAsc(eq(user))).thenReturn(Optional.of(first));

        boolean result = service.needsBaselineBefore(user, LocalDate.of(2026, 5, 31));

        assertTrue(result);
    }

    @Test
    void resolveUserSnapshotForDate_keepsHistoricalNullScheduleEffectiveDate() {
        User user = new User();
        user.setId(1L);
        user.setUsername("worker");
        user.setScheduleEffectiveDate(LocalDate.of(2026, 3, 1));

        UserEmploymentModelHistory history = new UserEmploymentModelHistory();
        history.setModelType(EmploymentModelType.STANDARD);
        history.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        history.setDailyWorkHours(8.0);
        history.setExpectedWorkDays(5);
        history.setScheduleEffectiveDate(null);

        when(historyRepository.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(eq(user), any()))
                .thenReturn(Optional.of(history));

        User snapshot = service.resolveUserSnapshotForDate(user, LocalDate.of(2026, 2, 1));

        assertNull(snapshot.getScheduleEffectiveDate());
    }
}
