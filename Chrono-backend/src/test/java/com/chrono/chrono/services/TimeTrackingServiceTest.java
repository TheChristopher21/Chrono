package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.DailyNote;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
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
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Mock
    private EmploymentModelHistoryService employmentModelHistoryService;

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
    void getDailySummary_closesDayWhenStartAndEndShareTheSameMinute() {
        TimeTrackingEntry start = entry(user, date.atTime(17, 39), TimeTrackingEntry.PunchType.START);
        start.setId(10L);
        TimeTrackingEntry end = entry(user, date.atTime(17, 39), TimeTrackingEntry.PunchType.ENDE);
        end.setId(11L);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date))
                .thenReturn(List.of(end, start));
        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());

        DailyTimeSummaryDTO summary = timeTrackingService.getDailySummary("alice", date);

        assertEquals(0, summary.getWorkedMinutes());
        assertEquals(LocalTime.of(17, 39), summary.getPrimaryTimes().getFirstStartTime());
        assertEquals(LocalTime.of(17, 39), summary.getPrimaryTimes().getLastEndTime());
        assertFalse(summary.getPrimaryTimes().isOpen());
        assertEquals(TimeTrackingEntry.PunchType.START, summary.getEntries().get(0).getPunchType());
        assertEquals(TimeTrackingEntry.PunchType.ENDE, summary.getEntries().get(1).getPunchType());
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
    void computeDailyWorkDifference_regularVacationWithPunchesCountsAgainstNormalExpectedTime() {
        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, date.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest regularVacation = new VacationRequest();
        regularVacation.setApproved(true);
        regularVacation.setStartDate(date);
        regularVacation.setEndDate(date);

        when(dailyNoteRepository.findByUserAndNoteDate(user, date)).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenAnswer(invocation -> {
            List<VacationRequest> vacations = invocation.getArgument(2);
            return vacations.isEmpty() ? 480 : 0;
        });

        int diff = timeTrackingService.computeDailyWorkDifference(user, date, List.of(regularVacation), List.of(start, end));

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


    @Test
    void handlePunch_removesSingleDayVacationOnPunchDay() {
        VacationRequest vacation = new VacationRequest();
        vacation.setId(10L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setStartDate(LocalDate.now(java.time.ZoneId.of("Europe/Berlin")));
        vacation.setEndDate(LocalDate.now(java.time.ZoneId.of("Europe/Berlin")));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), any(LocalDate.class), any())).thenReturn(480);

        timeTrackingService.handlePunch("alice", TimeTrackingEntry.PunchSource.MANUAL_PUNCH, null, null, null, null, null);

        verify(vacationRequestRepository).delete(vacation);
    }


    @Test
    void handlePunch_removesCompanyVacationOnPunchDay() {
        VacationRequest vacation = new VacationRequest();
        vacation.setId(11L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setCompanyVacation(true);
        vacation.setStartDate(LocalDate.now(java.time.ZoneId.of("Europe/Berlin")));
        vacation.setEndDate(LocalDate.now(java.time.ZoneId.of("Europe/Berlin")));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), any(LocalDate.class), any())).thenReturn(480);

        timeTrackingService.handlePunch("alice", TimeTrackingEntry.PunchSource.MANUAL_PUNCH, null, null, null, null, null);

        verify(vacationRequestRepository).delete(vacation);
    }

    @Test
    void handlePunch_splitsVacationRangeWhenPunchInTheMiddle() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
        VacationRequest vacation = new VacationRequest();
        vacation.setId(20L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setStartDate(today.minusDays(1));
        vacation.setEndDate(today.plusDays(1));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(vacationRequestRepository.save(any(VacationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), any(LocalDate.class), any())).thenReturn(480);

        timeTrackingService.handlePunch("alice", TimeTrackingEntry.PunchSource.MANUAL_PUNCH, null, null, null, null, null);

        assertEquals(today.minusDays(1), vacation.getStartDate());
        assertEquals(today.minusDays(1), vacation.getEndDate());
        verify(vacationRequestRepository, times(2)).save(any(VacationRequest.class));
    }

    @Test
    void handlePunch_splitsOvertimeVacationDeductionAcrossRemainingRanges() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
        VacationRequest vacation = new VacationRequest();
        vacation.setId(21L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setUsesOvertime(true);
        vacation.setOvertimeDeductionMinutes(180);
        vacation.setStartDate(today.minusDays(1));
        vacation.setEndDate(today.plusDays(1));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(vacationRequestRepository.save(any(VacationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), any(LocalDate.class), any())).thenReturn(480);

        timeTrackingService.handlePunch("alice", TimeTrackingEntry.PunchSource.MANUAL_PUNCH, null, null, null, null, null);

        assertEquals(90, vacation.getOvertimeDeductionMinutes());
        verify(vacationRequestRepository).save(argThat(saved ->
                saved != vacation
                        && saved.getStartDate().equals(today.plusDays(1))
                        && saved.getEndDate().equals(today.plusDays(1))
                        && Integer.valueOf(90).equals(saved.getOvertimeDeductionMinutes())
        ));
    }

    @Test
    void handlePunch_infersProjectFromTaskBeforeSaving() {
        Company company = new Company();
        company.setCustomerTrackingEnabled(true);
        user.setCompany(company);

        Customer customer = new Customer();
        customer.setId(100L);

        Project project = new Project();
        project.setId(200L);
        project.setCustomer(customer);

        Task task = new Task();
        task.setId(300L);
        task.setProject(project);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(taskRepository.findById(300L)).thenReturn(Optional.of(task));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(Collections.emptyList());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());

        TimeTrackingEntryDTO dto = timeTrackingService.handlePunch(
                "alice",
                TimeTrackingEntry.PunchSource.MANUAL_PUNCH,
                100L,
                null,
                300L,
                null,
                null
        );

        assertNotNull(dto);
        assertEquals(project.getId(), dto.getProjectId());
    }

    @Test
    void handlePunch_infersSingleCustomerProjectWhenNoProjectWasSelected() {
        Company company = new Company();
        company.setCustomerTrackingEnabled(true);
        user.setCompany(company);

        Customer customer = new Customer();
        customer.setId(101L);

        Project onlyProject = new Project();
        onlyProject.setId(201L);
        onlyProject.setCustomer(customer);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(customerRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByCustomerIdOrderByNameAsc(101L)).thenReturn(List.of(onlyProject));
        when(timeTrackingEntryRepository.findLastEntryByUserAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(timeTrackingEntryRepository.save(any(TimeTrackingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(Collections.emptyList());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());

        TimeTrackingEntryDTO dto = timeTrackingService.handlePunch(
                "alice",
                TimeTrackingEntry.PunchSource.MANUAL_PUNCH,
                101L,
                null,
                null,
                null,
                null
        );

        assertNotNull(dto);
        assertEquals(onlyProject.getId(), dto.getProjectId());
    }


    @Test
    void clearVacationOnPunchDay_ignoresNonChargeableDaysWithinVacationRange() throws Exception {
        VacationRequest vacation = new VacationRequest();
        vacation.setId(99L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setUsesOvertime(true);
        vacation.setStartDate(LocalDate.of(2024, 1, 5));
        vacation.setEndDate(LocalDate.of(2024, 1, 8));
        vacation.setOvertimeDeductionMinutes(960);

        LocalDate saturday = LocalDate.of(2024, 1, 6);

        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(saturday), any())).thenReturn(0);

        invokeClearVacationOnPunchDay(user, saturday);

        assertEquals(LocalDate.of(2024, 1, 5), vacation.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 8), vacation.getEndDate());
        assertEquals(960, vacation.getOvertimeDeductionMinutes());
        verify(vacationRequestRepository, times(0)).save(any(VacationRequest.class));
        verify(vacationRequestRepository, times(0)).delete(any(VacationRequest.class));
    }

    @Test
    void clearVacationOnPunchDay_splitsOvertimeDeductionByChargeableDaysNotCalendarDays() throws Exception {
        VacationRequest vacation = new VacationRequest();
        vacation.setId(100L);
        vacation.setUser(user);
        vacation.setApproved(true);
        vacation.setUsesOvertime(true);
        vacation.setStartDate(LocalDate.of(2024, 1, 4));
        vacation.setEndDate(LocalDate.of(2024, 1, 8));
        vacation.setOvertimeDeductionMinutes(1440);

        LocalDate punchDay = LocalDate.of(2024, 1, 5);
        LocalDate thursday = LocalDate.of(2024, 1, 4);
        LocalDate saturday = LocalDate.of(2024, 1, 6);
        LocalDate sunday = LocalDate.of(2024, 1, 7);
        LocalDate monday = LocalDate.of(2024, 1, 8);

        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(punchDay), any())).thenReturn(480);
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(thursday), any())).thenReturn(480);
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(saturday), any())).thenReturn(0);
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(sunday), any())).thenReturn(0);
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(monday), any())).thenReturn(480);

        invokeClearVacationOnPunchDay(user, punchDay);

        assertEquals(LocalDate.of(2024, 1, 4), vacation.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 4), vacation.getEndDate());
        assertEquals(720, vacation.getOvertimeDeductionMinutes());

        verify(vacationRequestRepository).save(argThat(saved ->
                saved != vacation
                        && saved.getStartDate().equals(LocalDate.of(2024, 1, 6))
                        && saved.getEndDate().equals(LocalDate.of(2024, 1, 8))
                        && Integer.valueOf(720).equals(saved.getOvertimeDeductionMinutes())
        ));
    }

    @Test
    void getWeeklyBalance_percentageUserIgnoresVacationOnWorkedDays() {
        user.setIsPercentage(true);
        user.setWorkPercentage(100);
        user.setExpectedWorkDays(5);

        LocalDate monday = LocalDate.of(2024, 1, 1);
        TimeTrackingEntry start = entry(user, monday.atTime(8, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, monday.atTime(16, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest vacation = new VacationRequest();
        vacation.setApproved(true);
        vacation.setStartDate(monday);
        vacation.setEndDate(monday);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(eq(user), any(), any()))
                .thenReturn(List.of(start, end));
        when(workScheduleService.getExpectedWeeklyMinutesForPercentageUser(eq(user), eq(monday), eq(monday.plusDays(6)), argThat(List::isEmpty))).thenReturn(2550);
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), eq(monday))).thenReturn(Optional.empty());

        int diff = timeTrackingService.getWeeklyBalance(user, monday);

        assertEquals(-2070, diff);
    }

    @Test
    void rebuildUserBalance_subtractsOvertimeVacationDeductions() {
        user.setTrackingBalanceInMinutes(0);

        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, date.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest overtimeVacation = new VacationRequest();
        overtimeVacation.setApproved(true);
        overtimeVacation.setUsesOvertime(true);
        overtimeVacation.setStartDate(date);
        overtimeVacation.setEndDate(date);
        overtimeVacation.setOvertimeDeductionMinutes(120);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(List.of(end, start));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(overtimeVacation));
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenReturn(480);
        when(payslipRepository.findByUser(user)).thenReturn(Collections.emptyList());

        timeTrackingService.rebuildUserBalance(user);

        assertEquals(-120, user.getTrackingBalanceInMinutes());
        verify(userRepository).save(user);
    }

    @Test
    void rebuildUserBalance_subtractsApprovedOvertimePayoutsAndOvertimeVacationDeductions() {
        user.setTrackingBalanceInMinutes(0);

        TimeTrackingEntry start = entry(user, date.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, date.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest overtimeVacation = new VacationRequest();
        overtimeVacation.setApproved(true);
        overtimeVacation.setUsesOvertime(true);
        overtimeVacation.setStartDate(date);
        overtimeVacation.setEndDate(date);
        overtimeVacation.setOvertimeDeductionMinutes(60);

        Payslip approvedPayout = new Payslip();
        approvedPayout.setApproved(true);
        approvedPayout.setPayoutOvertime(true);
        approvedPayout.setOvertimeHours(1.0);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(List.of(end, start));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(overtimeVacation));
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(date), any())).thenReturn(480);
        when(payslipRepository.findByUser(user)).thenReturn(List.of(approvedPayout));

        timeTrackingService.rebuildUserBalance(user);

        assertEquals(-120, user.getTrackingBalanceInMinutes());
        verify(userRepository).save(user);
    }


    @Test
    void rebuildUserBalance_percentageUserUsesOnlyElapsedDaysOfCurrentWeek() {
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(5);
        user.setTrackingBalanceInMinutes(0);

        LocalDate monday = LocalDate.of(2026, 3, 23);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(payslipRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(workScheduleService.getExpectedWeeklyMinutesForPercentageUser(eq(user), eq(monday), eq(monday), any())).thenReturn(306);

        TimeTrackingService spyService = spy(timeTrackingService);
        doReturn(monday).when(spyService).getCurrentBerlinDate();

        spyService.rebuildUserBalance(user);

        assertEquals(-306, user.getTrackingBalanceInMinutes());
    }

    @Test
    void rebuildUserBalance_ignoresFutureOvertimeVacationDeductionUntilVacationStarts() {
        user.setTrackingBalanceInMinutes(0);

        LocalDate today = LocalDate.of(2026, 3, 23);
        VacationRequest overtimeVacation = new VacationRequest();
        overtimeVacation.setApproved(true);
        overtimeVacation.setUsesOvertime(true);
        overtimeVacation.setStartDate(today.plusDays(10));
        overtimeVacation.setEndDate(today.plusDays(10));
        overtimeVacation.setOvertimeDeductionMinutes(510);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(Collections.emptyList());
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(overtimeVacation));
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());

        TimeTrackingService spyService = spy(timeTrackingService);
        doReturn(today).when(spyService).getCurrentBerlinDate();

        spyService.rebuildUserBalance(user);

        assertEquals(0, user.getTrackingBalanceInMinutes());
    }

    @Test
    void rebuildUserBalance_ignoresHourlyPeriodBeforeSwitchToStandardUser() {
        user.setIsHourly(false);
        user.setTrackingBalanceInMinutes(0);

        LocalDate hourlyDay = LocalDate.of(2026, 4, 6);
        LocalDate standardDay = LocalDate.of(2026, 4, 8);

        TimeTrackingEntry danglingStartFromHourlyPeriod = entry(user, hourlyDay.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry startStandardDay = entry(user, standardDay.atTime(8, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry endStandardDay = entry(user, standardDay.atTime(12, 0), TimeTrackingEntry.PunchType.ENDE);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user))
                .thenReturn(List.of(endStandardDay, startStandardDay, danglingStartFromHourlyPeriod));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(payslipRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(employmentModelHistoryService.resolveCurrentOvertimeStreakStart(user)).thenReturn(LocalDate.of(2026, 4, 8));
        when(workScheduleService.computeExpectedWorkMinutes(eq(user), eq(standardDay), any())).thenReturn(240);

        timeTrackingService.rebuildUserBalance(user);

        assertEquals(0, user.getTrackingBalanceInMinutes());
        verify(workScheduleService, times(0)).computeExpectedWorkMinutes(eq(user), eq(hourlyDay), any());
    }

    @Test
    void rebuildUserBalance_usesHistoricalPercentageModelBeforeFutureStandardSwitch() {
        user.setIsPercentage(false);
        user.setIsHourly(false);
        user.setDailyWorkHours(8.5);
        user.setTrackingBalanceInMinutes(0);

        LocalDate today = LocalDate.of(2026, 4, 27);
        TimeTrackingEntry start = entry(user, today.atTime(9, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, today.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE);

        User historicalPercentageUser = new User();
        historicalPercentageUser.setId(user.getId());
        historicalPercentageUser.setUsername(user.getUsername());
        historicalPercentageUser.setIsPercentage(true);
        historicalPercentageUser.setIsHourly(false);
        historicalPercentageUser.setWorkPercentage(60);
        historicalPercentageUser.setExpectedWorkDays(5);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user)).thenReturn(List.of(end, start));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(Collections.emptyList());
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(employmentModelHistoryService.resolveUserSnapshotForDate(eq(user), eq(today))).thenReturn(historicalPercentageUser);
        when(workScheduleService.getExpectedWeeklyMinutesForPercentageUser(eq(historicalPercentageUser), eq(today), eq(today), any())).thenReturn(306);
        when(payslipRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(dailyNoteRepository.findByUserAndNoteDate(any(User.class), eq(today))).thenReturn(Optional.empty());

        TimeTrackingService spyService = spy(timeTrackingService);
        doReturn(today).when(spyService).getCurrentBerlinDate();

        spyService.rebuildUserBalance(user);

        assertEquals(174, user.getTrackingBalanceInMinutes());
        verify(workScheduleService, times(0)).computeExpectedWorkMinutes(eq(user), eq(today), any());
    }

    @Test
    void getWeeklyBalance_percentageUserKeepsVacationOnOtherDaysWhenOneVacationDayHasPunches() {
        user.setIsPercentage(true);
        user.setWorkPercentage(100);
        user.setExpectedWorkDays(5);

        LocalDate monday = LocalDate.of(2024, 1, 1);
        LocalDate tuesday = monday.plusDays(1);
        TimeTrackingEntry start = entry(user, tuesday.atTime(8, 0), TimeTrackingEntry.PunchType.START);
        TimeTrackingEntry end = entry(user, tuesday.atTime(16, 0), TimeTrackingEntry.PunchType.ENDE);

        VacationRequest vacation = new VacationRequest();
        vacation.setApproved(true);
        vacation.setStartDate(monday);
        vacation.setEndDate(tuesday);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(vacationRequestRepository.findByUserAndApprovedTrue(user)).thenReturn(List.of(vacation));
        when(timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(eq(user), any(), any()))
                .thenReturn(List.of(start, end));
        when(workScheduleService.getExpectedWeeklyMinutesForPercentageUser(eq(user), eq(monday), eq(monday.plusDays(6)), argThat(vacations ->
                vacations.size() == 1
                        && vacations.get(0).getStartDate().equals(monday)
                        && vacations.get(0).getEndDate().equals(monday)))).thenReturn(1920);
        when(dailyNoteRepository.findByUserAndNoteDate(eq(user), eq(tuesday))).thenReturn(Optional.empty());

        int diff = timeTrackingService.getWeeklyBalance(user, monday);

        assertEquals(-1440, diff);
    }

    private void invokeClearVacationOnPunchDay(User user, LocalDate punchDay) throws Exception {
        Method method = TimeTrackingService.class.getDeclaredMethod("clearVacationOnPunchDay", User.class, LocalDate.class);
        method.setAccessible(true);
        method.invoke(timeTrackingService, user, punchDay);
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
