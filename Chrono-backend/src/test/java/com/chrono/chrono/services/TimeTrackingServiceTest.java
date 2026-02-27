package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.entities.DailyNote;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.DailyNoteRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.TaskRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeTrackingServiceTest {

    @Mock
    private TimeTrackingEntryRepository timeTrackingEntryRepository;
    @Mock
    private WorkScheduleService workScheduleService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VacationRequestRepository vacationRequestRepository;
    @Mock
    private SickLeaveRepository sickLeaveRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private DailyNoteRepository dailyNoteRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private PayslipRepository payslipRepository;

    @InjectMocks
    private TimeTrackingService timeTrackingService;

    private User user;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        date = LocalDate.of(2024, 1, 3);
    }

    @Test
    void getDailySummary_calculatesWorkedAndBreakMinutes() {
        TimeTrackingEntry startMorning = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry endMorning = entry(user, date.atTime(12, 0), TimeTrackingEntry.PunchType.ENDE);
        TimeTrackingEntry startAfternoon = entry(user, date.atTime(13, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry endAfternoon = entry(user, date.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date))
                .thenReturn(List.of(startMorning, endMorning, startAfternoon, endAfternoon));
        DailyNote note = new DailyNote();
        note.setContent("Abschluss erstellt");
        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.of(note));

        DailyTimeSummaryDTO summary = timeTrackingService.getDailySummary("alice", date);

        assertEquals("alice", summary.getUsername());
        assertEquals(420, summary.getWorkedMinutes());
        assertEquals(60, summary.getBreakMinutes());
        assertEquals("Abschluss erstellt", summary.getDailyNote());
        assertFalse(summary.isNeedsCorrection());
        assertEquals(LocalTime.of(9, 0), summary.getPrimaryTimes().getFirstStartTime());
        assertEquals(LocalTime.of(17, 0), summary.getPrimaryTimes().getLastEndTime());
        assertFalse(summary.getPrimaryTimes().isOpen());
        assertEquals(4, summary.getEntries().size());
    }

    @Test
    void computeDailyWorkDifference_usesExpectedMinutesFromSchedule() {
        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, date.atTime(16, 0), TimeTrackingEntry.PunchType.ENDE);

        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenReturn(480);

        int diff = timeTrackingService.computeDailyWorkDifference(user, date, Collections.emptyList(), List.of(start, end));

        assertEquals(-60, diff);
    }


    @Test
    void computeDailyWorkDifference_companyVacationWithPunchesCountsWorkedTimeInsteadOfVacation() {
        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, date.atTime(16, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest companyVacation = new VacationRequest();
        companyVacation.setApproved(true);
        companyVacation.setCompanyVacation(true);
        companyVacation.setStartDate(date);
        companyVacation.setEndDate(date);

        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenAnswer(invocation -> {
            List<VacationRequest> vacations = invocation.getArgument(2);
            return vacations.stream().anyMatch(VacationRequest::isCompanyVacation) ? 0 : 480;
        });

        int diff = timeTrackingService.computeDailyWorkDifference(user, date, List.of(companyVacation), List.of(start, end));

        assertEquals(-60, diff);
    }

    @Test
    void computeDailyWorkDifference_companyVacationWithoutPunchesStaysVacationDay() {
        VacationRequest companyVacation = new VacationRequest();
        companyVacation.setApproved(true);
        companyVacation.setCompanyVacation(true);
        companyVacation.setStartDate(date);
        companyVacation.setEndDate(date);

        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenAnswer(invocation -> {
            List<VacationRequest> vacations = invocation.getArgument(2);
            return vacations.stream().anyMatch(VacationRequest::isCompanyVacation) ? 0 : 480;
        });

        int diff = timeTrackingService.computeDailyWorkDifference(user, date, List.of(companyVacation), Collections.emptyList());

        assertEquals(0, diff);
    }

    @Test
    void getDailySummary_marksOpenDayWhenLastEntryIsStart() {
        TimeTrackingEntry startMorning = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry endMorning = entry(user, date.atTime(12, 0), TimeTrackingEntry.PunchType.ENDE);
        TimeTrackingEntry startAfternoon = entry(user, date.atTime(13, 0), TimeTrackingEntry.PunchType.START);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date))
                .thenReturn(List.of(startMorning, endMorning, startAfternoon));
        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());

        DailyTimeSummaryDTO summary = timeTrackingService.getDailySummary("alice", date);

        assertEquals(180, summary.getWorkedMinutes());
        assertEquals(60, summary.getBreakMinutes());
        assertTrue(summary.getPrimaryTimes().isOpen());
        assertEquals(LocalTime.of(9, 0), summary.getPrimaryTimes().getFirstStartTime());
        assertNull(summary.getPrimaryTimes().getLastEndTime());
    }

    @Test
    void getDailySummary_flagsNeedsCorrectionForAutoEndPunch() {
        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry autoEnd = entry(user, date.atTime(23, 20), TimeTrackingEntry.PunchType.ENDE);
        autoEnd.setSource(TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date))
                .thenReturn(List.of(start, autoEnd));
        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());

        DailyTimeSummaryDTO summary = timeTrackingService.getDailySummary("alice", date);

        assertTrue(summary.isNeedsCorrection());
    }

    private TimeTrackingEntry entry(User user, LocalDateTime timestamp, TimeTrackingEntry.PunchType type) {
        TimeTrackingEntry entry = new TimeTrackingEntry();
        entry.setUser(user);
        entry.setEntryTimestamp(timestamp);
        entry.setPunchType(type);
        entry.setSource(TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        return entry;
    }
}
