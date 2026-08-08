package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.dto.TimeTrackingImportRowDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.entities.DailyNote;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.DailyNoteRepository;
import com.chrono.chrono.repositories.TaskRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import org.apache.poi.ss.usermodel.*; // Für Excel-Verarbeitung
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Objects;


@Service
public class TimeTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);
    private static final long MAX_PERIOD_DAYS = 366;

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;
    @Autowired
    private WorkScheduleService workScheduleService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VacationRequestRepository vacationRequestRepository;
    @Autowired
    private SickLeaveRepository sickLeaveRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private DailyNoteRepository dailyNoteRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private EmploymentModelHistoryService employmentModelHistoryService;

    private User loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    private boolean sameCompany(User user, Customer customer) {
        return user != null
                && user.getCompany() != null
                && customer != null
                && ((customer.getCompany() == null && user.getCompany().getId() == null)
                    || (customer.getCompany() != null
                        && Objects.equals(user.getCompany().getId(), customer.getCompany().getId())));
    }

    private Customer resolveCustomerForUser(Long customerId, User user) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        if (!sameCompany(user, customer)) {
            throw new SecurityException("Customer belongs to another company");
        }
        return customer;
    }

    private Project resolveProjectForUser(Long projectId, User user) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (project.getCustomer() == null || !sameCompany(user, project.getCustomer())) {
            throw new SecurityException("Project belongs to another company");
        }
        return project;
    }

    private Task resolveTaskForUser(Long taskId, User user) {
        if (taskId == null) {
            return null;
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Project project = task.getProject();
        if (project == null || project.getCustomer() == null || !sameCompany(user, project.getCustomer())) {
            throw new SecurityException("Task belongs to another company");
        }
        return task;
    }

    private boolean hasRole(User user, String roleName) {
        return user != null
                && user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> roleName.equals(role.getRoleName()));
    }

    private boolean canAdminAccess(User requester, User target) {
        return hasRole(requester, "ROLE_SUPERADMIN")
                || (hasRole(requester, "ROLE_ADMIN")
                    && requester.getCompany() != null
                    && target.getCompany() != null
                    && Objects.equals(requester.getCompany().getId(), target.getCompany().getId()));
    }

    private Project resolveImplicitProject(User user, Customer customer, Project project, Task task) {
        if (project != null) {
            return project;
        }
        if (task != null && task.getProject() != null) {
            return task.getProject();
        }
        if (customer == null || user == null || user.getCompany() == null
                || !Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            return null;
        }

        List<Project> customerProjects = projectRepository.findByCustomerIdOrderByNameAsc(customer.getId());
        if (customerProjects.size() == 1) {
            return customerProjects.get(0);
        }
        return null;
    }

    @Transactional
    public TimeTrackingEntryDTO handlePunch(String username, TimeTrackingEntry.PunchSource source, Long customerId, Long projectId, Long taskId, Integer durationMinutes, String description) {
        User user = loadUserByUsername(username);
        Customer customer = null;
        if (customerId != null && user.getCompany() != null && Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            customer = resolveCustomerForUser(customerId, user);
        }
        Project project = null;
        if (projectId != null && user.getCompany() != null && Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            project = resolveProjectForUser(projectId, user);
        }
        Task task = null;
        if (taskId != null) {
            task = resolveTaskForUser(taskId, user);
        }
        // Default to CET/Berlin timezone for all automatic timestamps
        LocalDate today = getCurrentBerlinDate();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Berlin")).truncatedTo(ChronoUnit.MINUTES);

        Optional<TimeTrackingEntry> lastEntryOpt = timeTrackingEntryRepository.findLastEntryByUserAndDate(user, today);

        TimeTrackingEntry.PunchType nextPunchType;
        if (lastEntryOpt.isEmpty()) {
            nextPunchType = TimeTrackingEntry.PunchType.START;
        } else {
            nextPunchType = (lastEntryOpt.get().getPunchType() == TimeTrackingEntry.PunchType.START) ?
                    TimeTrackingEntry.PunchType.ENDE : TimeTrackingEntry.PunchType.START;
        }

        if (nextPunchType == TimeTrackingEntry.PunchType.START && customer == null) {
            if (user.getLastCustomer() != null) {
                customer = user.getLastCustomer();
            }
        }

        if (nextPunchType == TimeTrackingEntry.PunchType.ENDE && customer == null && lastEntryOpt.isPresent()) {
            customer = lastEntryOpt.get().getCustomer();
        }
        if (nextPunchType == TimeTrackingEntry.PunchType.ENDE && project == null && lastEntryOpt.isPresent()) {
            project = lastEntryOpt.get().getProject();
        }

        project = resolveImplicitProject(user, customer, project, task);

        TimeTrackingEntry newEntry = new TimeTrackingEntry(user, customer, project, now, nextPunchType, source);
        newEntry.setTask(task);
        if (nextPunchType == TimeTrackingEntry.PunchType.ENDE && source == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END) {
            newEntry.setSystemGeneratedNote("Automatischer Arbeitsende-Stempel. Bitte korrigieren.");
        }

        if (description != null) {
            newEntry.setDescription(description);
        }
        if (durationMinutes != null) {
            newEntry.setDurationMinutes(durationMinutes);
        } else if (nextPunchType == TimeTrackingEntry.PunchType.ENDE && lastEntryOpt.isPresent()) {
            TimeTrackingEntry last = lastEntryOpt.get();
            if (last.getPunchType() == TimeTrackingEntry.PunchType.START) {
                int mins = (int) java.time.Duration.between(last.getEntryTimestamp(), now).toMinutes();
                newEntry.setDurationMinutes(mins);
            }
        }

        TimeTrackingEntry savedEntry = timeTrackingEntryRepository.save(newEntry);
        clearVacationOnPunchDay(user, today);
        if (savedEntry.getCustomer() != null) {
            user.setLastCustomer(savedEntry.getCustomer());
            userRepository.save(user);
        }
        logger.info("User '{}' punched {}. Timestamp: {}, Source: {}", username, nextPunchType, now, source);
        rebuildUserBalance(user); // Saldo nach jedem Stempel neu berechnen
        return TimeTrackingEntryDTO.fromEntity(savedEntry);
    }

    private void clearVacationOnPunchDay(User user, LocalDate punchDay) {
        List<VacationRequest> vacationRequests = vacationRequestRepository.findByUserAndApprovedTrue(user);
        List<VacationRequest> affectedVacations = vacationRequests.stream()
                .filter(vr -> !punchDay.isBefore(vr.getStartDate()) && !punchDay.isAfter(vr.getEndDate()))
                .toList();

        for (VacationRequest vacation : affectedVacations) {
            if (!isChargeableVacationDay(user, punchDay)) {
                logger.info("Urlaubseintrag {} für User '{}' bleibt unverändert, da {} kein anrechenbarer Urlaubstag ist.",
                        vacation.getId(), user.getUsername(), punchDay);
                continue;
            }

            LocalDate originalStart = vacation.getStartDate();
            LocalDate originalEnd = vacation.getEndDate();
            Integer originalOvertimeDeductionMinutes = vacation.getOvertimeDeductionMinutes();

            if (originalStart.equals(punchDay) && originalEnd.equals(punchDay)) {
                vacationRequestRepository.delete(vacation);
                logger.info("Urlaubseintrag {} für User '{}' wurde wegen Stempel am {} gelöscht.", vacation.getId(), user.getUsername(), punchDay);
                continue;
            }

            if (originalStart.equals(punchDay)) {
                vacation.setStartDate(originalStart.plusDays(1));
                vacation.setOvertimeDeductionMinutes(recalculateOvertimeDeductionMinutes(
                        originalOvertimeDeductionMinutes,
                        countChargeableVacationDays(user, vacation.getStartDate(), originalEnd),
                        0
                ));
                vacationRequestRepository.save(vacation);
                logger.info("Urlaubseintrag {} für User '{}' angepasst: neuer Start {} nach Stempel am {}.", vacation.getId(), user.getUsername(), vacation.getStartDate(), punchDay);
                continue;
            }

            if (originalEnd.equals(punchDay)) {
                vacation.setEndDate(originalEnd.minusDays(1));
                vacation.setOvertimeDeductionMinutes(recalculateOvertimeDeductionMinutes(
                        originalOvertimeDeductionMinutes,
                        countChargeableVacationDays(user, originalStart, vacation.getEndDate()),
                        0
                ));
                vacationRequestRepository.save(vacation);
                logger.info("Urlaubseintrag {} für User '{}' angepasst: neues Ende {} nach Stempel am {}.", vacation.getId(), user.getUsername(), vacation.getEndDate(), punchDay);
                continue;
            }

            LocalDate leftEnd = punchDay.minusDays(1);
            LocalDate rightStart = punchDay.plusDays(1);
            long leftChargeableDays = countChargeableVacationDays(user, originalStart, leftEnd);
            long rightChargeableDays = countChargeableVacationDays(user, rightStart, originalEnd);

            vacation.setEndDate(leftEnd);
            Integer leftDeductionMinutes = recalculateOvertimeDeductionMinutes(originalOvertimeDeductionMinutes, leftChargeableDays, rightChargeableDays);
            vacation.setOvertimeDeductionMinutes(leftDeductionMinutes);
            vacationRequestRepository.save(vacation);

            VacationRequest splitVacation = new VacationRequest();
            splitVacation.setUser(vacation.getUser());
            splitVacation.setStartDate(rightStart);
            splitVacation.setEndDate(originalEnd);
            splitVacation.setApproved(vacation.isApproved());
            splitVacation.setDenied(vacation.isDenied());
            splitVacation.setHalfDay(vacation.isHalfDay());
            splitVacation.setUsesOvertime(vacation.isUsesOvertime());
            splitVacation.setCompanyVacation(vacation.isCompanyVacation());
            splitVacation.setOvertimeDeductionMinutes(calculateRemainingOvertimeDeductionMinutes(originalOvertimeDeductionMinutes, leftDeductionMinutes));
            splitVacation.setAdminNote(vacation.getAdminNote());
            vacationRequestRepository.save(splitVacation);

            logger.info("Urlaubseintrag {} für User '{}' wurde wegen Stempel am {} aufgeteilt ({} bis {} und {} bis {}).",
                    vacation.getId(), user.getUsername(), punchDay, originalStart, vacation.getEndDate(), splitVacation.getStartDate(), splitVacation.getEndDate());
        }
    }

    private long countChargeableVacationDays(User user, LocalDate startDate, LocalDate endDate) {
        return countChargeableVacationDays(user, startDate, endDate, null);
    }

    private long countChargeableVacationDays(User user, LocalDate startDate, LocalDate endDate, Map<LocalDate, User> effectiveUserCache) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }

        long chargeableDays = 0;
        for (LocalDate current = startDate; !current.isAfter(endDate); current = current.plusDays(1)) {
            User effectiveUser = resolveEffectiveUserForBalanceDate(user, current, effectiveUserCache);
            if (isChargeableVacationDay(effectiveUser, current)) {
                chargeableDays++;
            }
        }
        return chargeableDays;
    }

    private boolean isChargeableVacationDay(User user, LocalDate date) {
        return workScheduleService.computeExpectedWorkMinutes(user, date, Collections.emptyList()) > 0;
    }

    private User resolveEffectiveUserForBalanceDate(User user, LocalDate date, Map<LocalDate, User> effectiveUserCache) {
        if (user == null || date == null || employmentModelHistoryService == null) {
            return user;
        }
        if (effectiveUserCache == null) {
            return resolveEffectiveUserSnapshot(user, date);
        }
        return effectiveUserCache.computeIfAbsent(date, d -> resolveEffectiveUserSnapshot(user, d));
    }

    private User resolveEffectiveUserSnapshot(User user, LocalDate date) {
        try {
            User snapshot = employmentModelHistoryService.resolveUserSnapshotForDate(user, date);
            return snapshot != null ? snapshot : user;
        } catch (Exception ex) {
            logger.warn("Konnte historischen Arbeitsmodell-Snapshot für User {} am {} nicht laden. Verwende aktuellen User-Zustand.",
                    user.getUsername(), date, ex);
            return user;
        }
    }

    private LocalDate findHistoricalBalanceSegmentEnd(User baseUser, LocalDate segmentStart, LocalDate maxEnd, Map<LocalDate, User> effectiveUserCache) {
        User firstSnapshot = resolveEffectiveUserForBalanceDate(baseUser, segmentStart, effectiveUserCache);
        LocalDate segmentEnd = segmentStart;
        while (segmentEnd.plusDays(1).compareTo(maxEnd) <= 0) {
            LocalDate next = segmentEnd.plusDays(1);
            User nextSnapshot = resolveEffectiveUserForBalanceDate(baseUser, next, effectiveUserCache);
            if (!hasSameBalanceRelevantConfig(firstSnapshot, nextSnapshot)) {
                break;
            }
            segmentEnd = next;
        }
        return segmentEnd;
    }

    private boolean hasSameBalanceRelevantConfig(User left, User right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getIsHourly(), right.getIsHourly())
                && Objects.equals(left.getIsPercentage(), right.getIsPercentage())
                && Objects.equals(left.getWorkPercentage(), right.getWorkPercentage())
                && Objects.equals(left.getExpectedWorkDays(), right.getExpectedWorkDays())
                && Objects.equals(left.getDailyWorkHours(), right.getDailyWorkHours())
                && Objects.equals(left.getScheduleCycle(), right.getScheduleCycle())
                && Objects.equals(left.getScheduleEffectiveDate(), right.getScheduleEffectiveDate())
                && Objects.equals(left.getWeeklySchedule(), right.getWeeklySchedule());
    }

    private Integer recalculateOvertimeDeductionMinutes(Integer originalMinutes, long keptChargeableDays, long otherChargeableDays) {
        if (originalMinutes == null || originalMinutes <= 0) {
            return originalMinutes;
        }
        long totalChargeableDays = keptChargeableDays + otherChargeableDays;
        if (totalChargeableDays <= 0) {
            return 0;
        }
        return Math.max((int) Math.round((double) originalMinutes * keptChargeableDays / totalChargeableDays), 0);
    }

    private Integer calculateRemainingOvertimeDeductionMinutes(Integer originalMinutes, Integer allocatedMinutes) {
        if (originalMinutes == null || originalMinutes <= 0) {
            return originalMinutes;
        }
        int safeAllocatedMinutes = allocatedMinutes != null ? allocatedMinutes : 0;
        return Math.max(originalMinutes - safeAllocatedMinutes, 0);
    }

    @Transactional
    public void autoEndDayForUsersWhoForgotPunchOut(LocalDate date) {
        List<User> usersToProcess = timeTrackingEntryRepository.findUsersWithLastEntryAsStartOnDate(date);
        LocalDateTime autoEndTime = date.atTime(23, 20); // Feste Zeit 23:20

        for (User user : usersToProcess) {
            User freshUser = userRepository.findById(user.getId()).orElse(null); // Hole frische User-Entität
            if (freshUser == null) {
                logger.warn("User mit ID {} nicht gefunden beim Auto-End Punch für Datum {}", user.getId(), date);
                continue;
            }
            if (Boolean.TRUE.equals(freshUser.getIsHourly())) {
                logger.info("Überspringe stündlichen Mitarbeiter {} für AutoPunchOut am {}.", freshUser.getUsername(), date);
                continue;
            }
            logger.info("Automatisches Arbeitsende für User '{}' am {} um 23:20 Uhr.", user.getUsername(), date);
            TimeTrackingEntry autoEndEntry = new TimeTrackingEntry(user, autoEndTime, TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END);
            autoEndEntry.setSystemGeneratedNote("Automatischer Arbeitsende-Stempel. Bitte korrigieren Sie die tatsächliche Endzeit.");
            timeTrackingEntryRepository.save(autoEndEntry);
            rebuildUserBalance(user); // Saldo neu berechnen
        }
    }

    @Transactional
    public TimeTrackingEntryDTO approveEntry(Long id) {
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setApproved(true);
        return TimeTrackingEntryDTO.fromEntity(timeTrackingEntryRepository.save(entry));
    }

    @Transactional
    public TimeTrackingEntryDTO approveEntry(Long id, String requestingUsername) {
        User requester = loadUserByUsername(requestingUsername);
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!canAdminAccess(requester, entry.getUser())) {
            throw new SecurityException("Not allowed");
        }
        entry.setApproved(true);
        return TimeTrackingEntryDTO.fromEntity(timeTrackingEntryRepository.save(entry));
    }

    @Transactional
    public TimeTrackingEntryDTO revokeApproval(Long id) {
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setApproved(false);
        return TimeTrackingEntryDTO.fromEntity(timeTrackingEntryRepository.save(entry));
    }

    @Transactional
    public TimeTrackingEntryDTO revokeApproval(Long id, String requestingUsername) {
        User requester = loadUserByUsername(requestingUsername);
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!canAdminAccess(requester, entry.getUser())) {
            throw new SecurityException("Not allowed");
        }
        entry.setApproved(false);
        return TimeTrackingEntryDTO.fromEntity(timeTrackingEntryRepository.save(entry));
    }

    public DailyTimeSummaryDTO getDailySummary(String username, LocalDate date) {
        User user = loadUserByUsername(username);
        List<TimeTrackingEntry> entries = sortEntriesChronologically(
                timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date)
        );
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);
        User effectiveUser = resolveEffectiveUserForBalanceDate(user, date, null);
        WorkScheduleService.ExpectedWorkContext context = workScheduleService.loadExpectedWorkContext(user, date, date);
        return calculateDailySummaryWithExpected(entries, user, effectiveUser, date, approvedVacations, context);
    }

    public List<TimeTrackingEntry> getTimeTrackingEntriesForUserAndDate(User user, LocalDate date) {
        // Stellt sicher, dass eine frische User-Entität verwendet wird, falls Caching involviert ist
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found: " + user.getUsername()));
        return timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(freshUser, date);
    }

    public List<DailyTimeSummaryDTO> getUserHistory(String username) {
        User user = loadUserByUsername(username);
        List<TimeTrackingEntry> allEntries = timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user);

        // Gruppiere Einträge nach Tag, wobei die Map nach Datum absteigend sortiert ist
        Map<LocalDate, List<TimeTrackingEntry>> entriesByDate = allEntries.stream()
                .filter(e -> e.getEntryDate() != null) // Null-Safety
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate,
                        () -> new TreeMap<>(Comparator.reverseOrder()), // Sortiert Map-Keys (Daten) absteigend
                        Collectors.toList()));

        return entriesByDate.entrySet().stream()
                .map(entry -> {
                    // Einträge innerhalb eines Tages aufsteigend sortieren für die Berechnung
                    List<TimeTrackingEntry> dailyEntriesSortedAsc = sortEntriesChronologically(entry.getValue());
                    return calculateDailySummaryFromEntries(dailyEntriesSortedAsc, user, entry.getKey());
                })
                .collect(Collectors.toList()); // Die resultierende Liste ist nach Datum absteigend sortiert wegen TreeMap
    }

    public List<DailyTimeSummaryDTO> getUserHistory(String username, LocalDate startDate, LocalDate endDate) {
        User user = loadUserByUsername(username);
        return getUserHistory(user, startDate, endDate);
    }

    public List<DailyTimeSummaryDTO> getUserHistory(User user, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return Collections.emptyList();
        }
        LocalDate start = startDate.isAfter(endDate) ? endDate : startDate;
        LocalDate end = endDate.isBefore(startDate) ? startDate : endDate;
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (periodDays > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("Der angefragte Zeitraum darf maximal 366 Tage umfassen.");
        }
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + user.getUsername()));

        List<TimeTrackingEntry> entries = timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(
                        freshUser,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()
                );
        Map<LocalDate, List<TimeTrackingEntry>> entriesByDate = entries.stream()
                .filter(entry -> entry.getEntryDate() != null)
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);
        Map<LocalDate, User> effectiveUsers = employmentModelHistoryService != null
                ? employmentModelHistoryService.resolveUserSnapshotsForRange(freshUser, start, end)
                : Collections.emptyMap();
        if (effectiveUsers == null) {
            effectiveUsers = Collections.emptyMap();
        }
        WorkScheduleService.ExpectedWorkContext context = workScheduleService.loadExpectedWorkContext(freshUser, start, end);

        List<DailyTimeSummaryDTO> summaries = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<TimeTrackingEntry> dailyEntries = sortEntriesChronologically(
                    entriesByDate.getOrDefault(date, Collections.emptyList())
            );
            User effectiveUser = effectiveUsers.getOrDefault(date, freshUser);
            summaries.add(calculateDailySummaryWithExpected(
                    dailyEntries,
                    freshUser,
                    effectiveUser,
                    date,
                    approvedVacations,
                    context
            ));
        }
        return summaries;
    }

    public TimePeriodSummaryDTO getUserPeriodSummary(User user, LocalDate startDate, LocalDate endDate) {
        List<DailyTimeSummaryDTO> summaries = getUserHistory(user, startDate, endDate);
        LocalDate accountingCutoff = getCurrentBerlinDate();
        List<DailyTimeSummaryDTO> evaluatedSummaries = summaries.stream()
                .filter(summary -> summary.getDate() != null && !summary.getDate().isAfter(accountingCutoff))
                .toList();
        int workedMinutes = evaluatedSummaries.stream().mapToInt(DailyTimeSummaryDTO::getWorkedMinutes).sum();
        int breakMinutes = evaluatedSummaries.stream().mapToInt(DailyTimeSummaryDTO::getBreakMinutes).sum();
        int expectedMinutes = evaluatedSummaries.stream()
                .map(DailyTimeSummaryDTO::getExpectedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int differenceMinutes = evaluatedSummaries.stream()
                .map(DailyTimeSummaryDTO::getDifferenceMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + user.getUsername()));
        LocalDate start = startDate != null && endDate != null && startDate.isAfter(endDate) ? endDate : startDate;
        LocalDate end = startDate != null && endDate != null && endDate.isBefore(startDate) ? startDate : endDate;

        return new TimePeriodSummaryDTO(
                freshUser.getUsername(),
                start,
                end,
                workedMinutes,
                breakMinutes,
                expectedMinutes,
                differenceMinutes,
                freshUser.getTrackingBalanceInMinutes(),
                summaries
        );
    }

    private List<TimeTrackingEntry> sortEntriesChronologically(List<TimeTrackingEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.stream()
                .sorted(Comparator
                        .comparing(
                                TimeTrackingEntry::getEntryTimestamp,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(entry -> entry.getId() != null ? entry.getId() : Long.MAX_VALUE))
                .collect(Collectors.toList());
    }

    private DailyTimeSummaryDTO.PrimaryTimes getPrimaryPunchTimes(List<TimeTrackingEntry> entries) {
        AtomicReference<LocalTime> firstStart = new AtomicReference<>();
        AtomicReference<LocalTime> lastEnd = new AtomicReference<>();
        boolean isOpen = false;

        if (entries != null && !entries.isEmpty()) {
            Optional<TimeTrackingEntry> firstStartEntry = entries.stream()
                    .filter(e -> e.getPunchType() == TimeTrackingEntry.PunchType.START)
                    .min(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp));
            firstStartEntry.ifPresent(entry -> firstStart.set(entry.getEntryTime()));

            Optional<TimeTrackingEntry> lastEndEntry = entries.stream()
                    .filter(e -> e.getPunchType() == TimeTrackingEntry.PunchType.ENDE)
                    .max(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp));
            lastEndEntry.ifPresent(entry -> lastEnd.set(entry.getEntryTime()));

            TimeTrackingEntry lastOverallEntry = entries.get(entries.size() - 1);
            if (lastOverallEntry.getPunchType() == TimeTrackingEntry.PunchType.START) {
                isOpen = true;
                // Wenn der Tag offen ist (letzter Stempel START), macht ein "lastEnd" keinen Sinn für die Primäranzeige
                lastEnd.set(null);
            }
            // Sicherstellen, dass lastEnd nicht vor firstStart liegt, wenn der Tag nicht offen ist
            if (firstStart.get() != null && lastEnd.get() != null && lastEnd.get().isBefore(firstStart.get()) && !isOpen) {
                lastEnd.set(null); // Ungültige Konstellation für eine abgeschlossene Periode
            }
        }
        return new DailyTimeSummaryDTO.PrimaryTimes(firstStart.get(), lastEnd.get(), isOpen);
    }

    private DailyTimeSummaryDTO calculateDailySummaryFromEntries(List<TimeTrackingEntry> entries, User user, LocalDate date) {
        List<TimeTrackingEntry> orderedEntries = sortEntriesChronologically(entries);
        Duration totalWorkTime = Duration.ZERO;
        Duration totalBreakTime = Duration.ZERO;
        LocalDateTime lastStartTime = null;
        LocalDateTime lastWorkEndTime = null;
        List<TimeTrackingEntryDTO> entryDTOs = orderedEntries.stream()
                .map(TimeTrackingEntryDTO::fromEntity)
                .collect(Collectors.toList());
        String dailyNoteContent = getDailyNoteContent(user, date);

        for (TimeTrackingEntry entry : orderedEntries) {
            if (entry.getPunchType() == TimeTrackingEntry.PunchType.START) {
                if (lastWorkEndTime != null && entry.getEntryTimestamp().isAfter(lastWorkEndTime)) {
                    totalBreakTime = totalBreakTime.plus(Duration.between(lastWorkEndTime, entry.getEntryTimestamp()));
                }
                lastStartTime = entry.getEntryTimestamp();
                lastWorkEndTime = null;
            } else if (entry.getPunchType() == TimeTrackingEntry.PunchType.ENDE) {
                if (lastStartTime != null && entry.getEntryTimestamp().isAfter(lastStartTime)) {
                    totalWorkTime = totalWorkTime.plus(Duration.between(lastStartTime, entry.getEntryTimestamp()));
                    lastWorkEndTime = entry.getEntryTimestamp();
                } else if (lastStartTime != null) { // ENDE vor START oder gleich -> ungültig für diesen Block
                    logger.warn("User {} am {}: ENDE-Stempel um {} ist nicht nach dem vorherigen START-Stempel um {}. Dieser Arbeitsblock wird nicht gezählt.",
                            user.getUsername(), date, entry.getEntryTimestamp(), lastStartTime);
                } else { // ENDE ohne vorherigen START in dieser Sequenz
                    logger.warn("User {} am {}: ENDE-Stempel um {} ohne vorherigen START-Stempel in der aktuellen Berechnungssequenz gefunden.", user.getUsername(), date, entry.getEntryTimestamp());
                }
                lastStartTime = null;
            }
        }

        boolean needsCorrection = orderedEntries.stream()
                .anyMatch(e -> e.getSource() == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END && !e.isCorrectedByUser());

        return new DailyTimeSummaryDTO(
                user.getUsername(),
                date,
                (int) totalWorkTime.toMinutes(),
                (int) totalBreakTime.toMinutes(),
                entryDTOs,
                dailyNoteContent,
                needsCorrection,
                getPrimaryPunchTimes(orderedEntries)
        );
    }

    private DailyTimeSummaryDTO calculateDailySummaryWithExpected(
            List<TimeTrackingEntry> entries,
            User displayUser,
            User scheduleUser,
            LocalDate date,
            List<VacationRequest> approvedVacations
    ) {
        return calculateDailySummaryWithExpected(
                entries,
                displayUser,
                scheduleUser,
                date,
                approvedVacations,
                null
        );
    }

    private DailyTimeSummaryDTO calculateDailySummaryWithExpected(
            List<TimeTrackingEntry> entries,
            User displayUser,
            User scheduleUser,
            LocalDate date,
            List<VacationRequest> approvedVacations,
            WorkScheduleService.ExpectedWorkContext context
    ) {
        DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(entries, displayUser, date);
        List<TimeTrackingEntry> safeEntries = entries != null ? entries : Collections.emptyList();
        List<VacationRequest> safeVacations = approvedVacations != null ? approvedVacations : Collections.emptyList();
        boolean hasTrackedEntries = summary.getWorkedMinutes() > 0 || !safeEntries.isEmpty();

        List<VacationRequest> vacationsForExpectedMinutes = safeVacations;
        if (hasTrackedEntries) {
            vacationsForExpectedMinutes = safeVacations.stream()
                    .filter(vr -> date.isBefore(vr.getStartDate()) || date.isAfter(vr.getEndDate()))
                    .collect(Collectors.toList());
        }

        User effectiveScheduleUser = scheduleUser != null ? scheduleUser : displayUser;
        int expectedMinutes = context == null
                ? workScheduleService.computeExpectedWorkMinutes(
                    effectiveScheduleUser,
                    date,
                    vacationsForExpectedMinutes
                )
                : workScheduleService.computeExpectedWorkMinutes(
                    effectiveScheduleUser,
                    date,
                    vacationsForExpectedMinutes,
                    context
                );

        summary.setExpectedMinutes(expectedMinutes);
        summary.setDifferenceMinutes(summary.getWorkedMinutes() - expectedMinutes);
        summary.setHasTrackedEntries(hasTrackedEntries);
        return summary;
    }

    @Transactional
    public void rebuildUserBalance(User user) {
        // Holt den frischesten Benutzerstatus aus der DB
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + user.getId()));

        LocalDate currentBalanceDate = getCurrentBerlinDate();
        User effectiveUserToday = resolveEffectiveUserForBalanceDate(freshUser, currentBalanceDate, new HashMap<>());

        // ====================== FINALE KORREKTUR FÜR STUNDENLÖHNER ======================
        if (Boolean.TRUE.equals(effectiveUserToday.getIsHourly())) {
            logger.info("Führe dedizierte Saldo-Neuberechnung für stundenbasierten Mitarbeiter {} durch.", freshUser.getUsername());

            List<TimeTrackingEntry> allEntries = timeTrackingEntryRepository.findByUserOrderByEntryTimestampAsc(freshUser);

            if (allEntries.isEmpty()) {
                freshUser.setTrackingBalanceInMinutes(0);
                userRepository.save(freshUser);
                logger.info("Keine Einträge für Stundenlöhner {}, Saldo auf 0 gesetzt.", freshUser.getUsername());
                return;
            }

            long totalWorkedMinutes = 0;
            LocalDateTime punchInTime = null;
            for (TimeTrackingEntry entry : allEntries) {
                // KORREKTUR: Enum-Vergleich anstelle von String-Vergleich
                if (TimeTrackingEntry.PunchType.START.equals(entry.getPunchType()) && punchInTime == null) {
                    punchInTime = entry.getEntryTimestamp();
                } else if (TimeTrackingEntry.PunchType.ENDE.equals(entry.getPunchType()) && punchInTime != null) {
                    Duration duration = Duration.between(punchInTime, entry.getEntryTimestamp());
                    totalWorkedMinutes += duration.toMinutes();
                    punchInTime = null;
                }
            }

            int paidOvertimeMinutes = payslipRepository.findByUser(freshUser).stream()
                    .filter(Payslip::isApproved)
                    .filter(Payslip::isPayoutOvertime)
                    .map(Payslip::getOvertimeHours)
                    .filter(Objects::nonNull)
                    .mapToInt(h -> (int) Math.round(h * 60))
                    .sum();
            totalWorkedMinutes -= paidOvertimeMinutes;

            freshUser.setTrackingBalanceInMinutes((int) totalWorkedMinutes);
            userRepository.save(freshUser);
            logger.info("Saldo für Stundenlöhner {} auf die Summe der gearbeiteten Zeit aktualisiert: {} Minuten.", freshUser.getUsername(), totalWorkedMinutes);
            return;
        }
        // ====================== ENDE DER FINALEN KORREKTUR ======================


        // Bestehende Logik für prozentuale und festangestellte Mitarbeiter (unverändert)
        logger.debug("TimeTrackingService.rebuildUserBalance: Entering rebuildUserBalance for user: '{}'. Config: isPercentage={}, isHourly={}", freshUser.getUsername(), freshUser.getIsPercentage(), freshUser.getIsHourly());

        List<TimeTrackingEntry> allEntriesForUser = timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(freshUser);
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);

        if (freshUser.getTrackingBalanceInMinutes() == null) {
            freshUser.setTrackingBalanceInMinutes(0);
        }

        boolean hasNoTrackedData = allEntriesForUser.isEmpty() && approvedVacations.isEmpty() && sickLeaveRepository.findByUser(freshUser).isEmpty();
        if (hasNoTrackedData && !Boolean.TRUE.equals(effectiveUserToday.getIsPercentage())) {
            if (freshUser.getTrackingBalanceInMinutes() != 0) {
                logger.info("Saldo für {} auf 0 gesetzt (keine Zeiteinträge oder relevante Abwesenheiten). Alter Saldo war: {}", freshUser.getUsername(), freshUser.getTrackingBalanceInMinutes());
                freshUser.setTrackingBalanceInMinutes(0);
                userRepository.save(freshUser);
            } else {
                logger.info("Saldo für {} bereits 0 und keine Einträge/Abwesenheiten.", freshUser.getUsername());
            }
            return;
        }

        LocalDate firstDayToConsider = currentBalanceDate;

        Optional<LocalDate> firstTrackingDayOpt = allEntriesForUser.stream().map(TimeTrackingEntry::getEntryDate).filter(Objects::nonNull).min(LocalDate::compareTo);
        Optional<LocalDate> firstVacationDayOpt = approvedVacations.stream().map(VacationRequest::getStartDate).min(LocalDate::compareTo);
        Optional<LocalDate> firstSickLeaveDayOpt = sickLeaveRepository.findByUser(freshUser).stream().map(com.chrono.chrono.entities.SickLeave::getStartDate).min(LocalDate::compareTo);

        firstDayToConsider = Stream.of(firstTrackingDayOpt, firstVacationDayOpt, firstSickLeaveDayOpt)
                .filter(Optional::isPresent).map(Optional::get).min(LocalDate::compareTo).orElse(firstDayToConsider);

        LocalDate today = currentBalanceDate;
        LocalDate lastDay = today;
        Optional<LocalDate> lastTrackingDayOpt = allEntriesForUser.stream().map(TimeTrackingEntry::getEntryDate).filter(Objects::nonNull).max(LocalDate::compareTo);
        if(lastTrackingDayOpt.isPresent() && lastTrackingDayOpt.get().isAfter(lastDay)){
            lastDay = lastTrackingDayOpt.get();
        }

        LocalDate balancePeriodStart = employmentModelHistoryService != null
                ? employmentModelHistoryService.resolveCurrentOvertimeStreakStart(freshUser)
                : null;
        if (balancePeriodStart != null && balancePeriodStart.isAfter(firstDayToConsider)) {
            logger.info("Saldo-Neuberechnung für {} beginnt erst ab {}. Frühere Stundenlohn-Zeiträume werden nicht als Überstundenkonto weitergeführt.",
                    freshUser.getUsername(), balancePeriodStart);
            firstDayToConsider = balancePeriodStart;
        }

        int totalMinutesBalance = 0;
        if (!firstDayToConsider.isAfter(lastDay)) {
            logger.info("Saldo-Neuberechnung für {}: Zeitraum {} bis {}", freshUser.getUsername(), firstDayToConsider, lastDay);
            Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate = allEntriesForUser.stream()
                    .filter(e -> e.getEntryDate() != null)
                    .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

            Map<LocalDate, User> effectiveUserCache = new HashMap<>();
            LocalDate currentBalanceDay = firstDayToConsider;
            while (!currentBalanceDay.isAfter(lastDay)) {
                User effectiveUser = resolveEffectiveUserForBalanceDate(freshUser, currentBalanceDay, effectiveUserCache);
                if (Boolean.TRUE.equals(effectiveUser.getIsPercentage())) {
                    LocalDate weekStart = currentBalanceDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    LocalDate weekEnd = weekStart.plusDays(6).isAfter(lastDay) ? lastDay : weekStart.plusDays(6);
                    LocalDate segmentEnd = findHistoricalBalanceSegmentEnd(freshUser, currentBalanceDay, weekEnd, effectiveUserCache);
                    totalMinutesBalance += computeWeeklyWorkDifferenceForPercentageUserInRange(
                            effectiveUser,
                            weekStart,
                            currentBalanceDay,
                            segmentEnd,
                            approvedVacations,
                            entriesGroupedByDate
                    );
                    currentBalanceDay = segmentEnd.plusDays(1);
                } else {
                    LocalDate d = currentBalanceDay;
                    List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(d, Collections.emptyList())
                            .stream().sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp)).collect(Collectors.toList());
                    int dailyDifference = computeDailyWorkDifference(effectiveUser, d, approvedVacations, entriesForDay);
                    totalMinutesBalance += dailyDifference;
                    if (logger.isTraceEnabled()) {
                        DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(entriesForDay, effectiveUser, d);
                        int expected = workScheduleService.computeExpectedWorkMinutes(effectiveUser, d, approvedVacations);
                        logger.trace("[User: {}] Saldo Tag {}: Ist: {}min, Soll: {}min, Diff: {}min, Saldo bisher: {}min",
                                freshUser.getUsername(), d, summary.getWorkedMinutes(), expected, dailyDifference, totalMinutesBalance - dailyDifference);
                    }
                    currentBalanceDay = currentBalanceDay.plusDays(1);
                }
            }
            int paidOvertimeMinutes = payslipRepository.findByUser(freshUser).stream()
                    .filter(Payslip::isApproved)
                    .filter(Payslip::isPayoutOvertime)
                    .filter(payslip -> {
                        LocalDate deductionDate = payslip.getPayoutDate() != null ? payslip.getPayoutDate() : payslip.getPeriodEnd();
                        return deductionDate == null || balancePeriodStart == null || !deductionDate.isBefore(balancePeriodStart);
                    })
                    .map(Payslip::getOvertimeHours)
                    .filter(Objects::nonNull)
                    .mapToInt(h -> (int) Math.round(h * 60))
                    .sum();
            totalMinutesBalance -= paidOvertimeMinutes;

            LocalDate balanceCutoff = lastDay;
            int overtimeVacationDeductionMinutes = approvedVacations.stream()
                    .filter(VacationRequest::isUsesOvertime)
                    .mapToInt(vacation -> calculateAppliedOvertimeVacationMinutesInRange(freshUser, vacation, balancePeriodStart, balanceCutoff, effectiveUserCache))
                    .sum();
            totalMinutesBalance -= overtimeVacationDeductionMinutes;

            if (freshUser.getTrackingBalanceInMinutes() != totalMinutesBalance) {
                logger.info("Saldo für {} aktualisiert von {} auf {} Minuten.", freshUser.getUsername(), freshUser.getTrackingBalanceInMinutes(), totalMinutesBalance);
                freshUser.setTrackingBalanceInMinutes(totalMinutesBalance);
            } else {
                logger.info("Saldo für {} ({} Minuten) hat sich nicht geändert und bleibt bestehen.", freshUser.getUsername(), totalMinutesBalance);
            }
        } else {
            logger.info("Kein relevanter Zeitraum ({} bis {}) für Saldo-Neuberechnung für {}. Saldo bleibt bei {}.", firstDayToConsider, lastDay, freshUser.getUsername(), freshUser.getTrackingBalanceInMinutes());
        }
        userRepository.save(freshUser);
    }

    private int getWorkedMinutesForDate(User user, LocalDate date, Map<LocalDate, List<TimeTrackingEntry>> allUserEntriesGroupedByDate) {
        List<TimeTrackingEntry> entriesForDay = allUserEntriesGroupedByDate.getOrDefault(date, Collections.emptyList())
                .stream().sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp)).collect(Collectors.toList());
        return entriesForDay.isEmpty() ? 0 : calculateDailySummaryFromEntries(entriesForDay, user, date).getWorkedMinutes();
    }

    private int computeWeeklyWorkDifferenceForPercentageUser(User user, LocalDate weekStart, LocalDate weekEvaluationEnd, List<VacationRequest> allApprovedVacationsForUser, Map<LocalDate, List<TimeTrackingEntry>> allUserEntriesGroupedByDate) {
        return computeWeeklyWorkDifferenceForPercentageUserInRange(user, weekStart, weekStart, weekEvaluationEnd, allApprovedVacationsForUser, allUserEntriesGroupedByDate);
    }

    private int computeWeeklyWorkDifferenceForPercentageUserInRange(User user, LocalDate weekStart, LocalDate rangeStart, LocalDate rangeEnd, List<VacationRequest> allApprovedVacationsForUser, Map<LocalDate, List<TimeTrackingEntry>> allUserEntriesGroupedByDate) {
        int totalWorkedMinutesInWeek = 0;
        LocalDate endOfWeek = weekStart.plusDays(6);
        LocalDate effectiveRangeStart = rangeStart.isBefore(weekStart) ? weekStart : rangeStart;
        LocalDate effectiveRangeEnd = rangeEnd.isBefore(endOfWeek) ? rangeEnd : endOfWeek;
        if (effectiveRangeStart.isAfter(effectiveRangeEnd)) {
            return 0;
        }
        for (LocalDate d = effectiveRangeStart; !d.isAfter(endOfWeek) && !d.isAfter(effectiveRangeEnd); d = d.plusDays(1)) {
            totalWorkedMinutesInWeek += getWorkedMinutesForDate(user, d, allUserEntriesGroupedByDate);
        }
        List<VacationRequest> approvedVacationsInThisWeek = expandVacationsExcludingWorkedDays(
                allApprovedVacationsForUser.stream()
                        .filter(vr -> vr.isApproved() && !vr.getEndDate().isBefore(effectiveRangeStart) && !vr.getStartDate().isAfter(effectiveRangeEnd))
                        .collect(Collectors.toList()),
                effectiveRangeStart,
                effectiveRangeEnd,
                allUserEntriesGroupedByDate
        );
        int expectedWeeklyMinutesAdjusted = effectiveRangeStart.equals(weekStart)
                ? workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, weekStart, effectiveRangeEnd, approvedVacationsInThisWeek)
                : workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, weekStart, effectiveRangeStart, effectiveRangeEnd, approvedVacationsInThisWeek);

        int weeklyDifference = totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjusted;
        logger.debug("User: {}, Woche ab: {}, Bereich: {} bis {}, Gearbeitet: {}min, Erwartet (bereinigt): {}min, Differenz diese Woche: {}min",
                user.getUsername(), weekStart, effectiveRangeStart, effectiveRangeEnd, totalWorkedMinutesInWeek, expectedWeeklyMinutesAdjusted, weeklyDifference);
        return weeklyDifference;
    }

    public int computeDailyWorkDifference(User user, LocalDate date, List<VacationRequest> approvedVacations, List<TimeTrackingEntry> entriesForDay) {
        DailyTimeSummaryDTO summary = calculateDailySummaryWithExpected(entriesForDay, user, user, date, approvedVacations);
        return summary.getDifferenceMinutes() != null ? summary.getDifferenceMinutes() : 0;
    }

    private List<VacationRequest> expandVacationsExcludingWorkedDays(List<VacationRequest> vacations,
                                                                   LocalDate rangeStart,
                                                                   LocalDate rangeEnd,
                                                                   Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate) {
        List<VacationRequest> result = new ArrayList<>();
        for (VacationRequest vacation : vacations) {
            LocalDate overlapStart = vacation.getStartDate().isAfter(rangeStart) ? vacation.getStartDate() : rangeStart;
            LocalDate overlapEnd = vacation.getEndDate().isBefore(rangeEnd) ? vacation.getEndDate() : rangeEnd;
            LocalDate segmentStart = null;
            for (LocalDate current = overlapStart; !current.isAfter(overlapEnd); current = current.plusDays(1)) {
                boolean hasWorkedEntries = !entriesGroupedByDate.getOrDefault(current, Collections.emptyList()).isEmpty();
                if (hasWorkedEntries) {
                    if (segmentStart != null) {
                        result.add(copyVacationSegment(vacation, segmentStart, current.minusDays(1)));
                        segmentStart = null;
                    }
                    continue;
                }
                if (segmentStart == null) {
                    segmentStart = current;
                }
            }
            if (segmentStart != null) {
                result.add(copyVacationSegment(vacation, segmentStart, overlapEnd));
            }
        }
        return result;
    }

    private VacationRequest copyVacationSegment(VacationRequest source, LocalDate startDate, LocalDate endDate) {
        VacationRequest segment = new VacationRequest();
        segment.setUser(source.getUser());
        segment.setApproved(source.isApproved());
        segment.setDenied(source.isDenied());
        segment.setHalfDay(source.isHalfDay() && startDate.equals(endDate));
        segment.setUsesOvertime(source.isUsesOvertime());
        segment.setCompanyVacation(source.isCompanyVacation());
        segment.setAdminNote(source.getAdminNote());
        segment.setStartDate(startDate);
        segment.setEndDate(endDate);
        segment.setOvertimeDeductionMinutes(source.getOvertimeDeductionMinutes());
        return segment;
    }

    private int calculateAppliedOvertimeVacationMinutesUpTo(User user, VacationRequest vacation, LocalDate balanceCutoff) {
        return calculateAppliedOvertimeVacationMinutesInRange(user, vacation, null, balanceCutoff, null);
    }

    private int calculateAppliedOvertimeVacationMinutesInRange(User user,
                                                               VacationRequest vacation,
                                                               LocalDate balanceStart,
                                                               LocalDate balanceCutoff,
                                                               Map<LocalDate, User> effectiveUserCache) {
        Integer originalMinutes = vacation.getOvertimeDeductionMinutes();
        if (originalMinutes == null || originalMinutes <= 0 || vacation.getStartDate() == null || vacation.getEndDate() == null) {
            return 0;
        }
        LocalDate effectiveStart = vacation.getStartDate();
        LocalDate effectiveEnd = vacation.getEndDate();

        if (balanceStart != null && effectiveEnd.isBefore(balanceStart)) {
            return 0;
        }
        if (balanceCutoff != null && effectiveStart.isAfter(balanceCutoff)) {
            return 0;
        }

        if (balanceStart != null && effectiveStart.isBefore(balanceStart)) {
            effectiveStart = balanceStart;
        }
        if (balanceCutoff != null && effectiveEnd.isAfter(balanceCutoff)) {
            effectiveEnd = balanceCutoff;
        }
        if (effectiveEnd.isBefore(effectiveStart)) {
            return 0;
        }

        long totalChargeableDays = countChargeableVacationDays(user, vacation.getStartDate(), vacation.getEndDate(), effectiveUserCache);
        if (totalChargeableDays <= 0) {
            return 0;
        }
        long applicableChargeableDays = countChargeableVacationDays(user, effectiveStart, effectiveEnd, effectiveUserCache);
        if (applicableChargeableDays <= 0) {
            return 0;
        }

        if (applicableChargeableDays >= totalChargeableDays) {
            return originalMinutes;
        }
        return Math.max((int) Math.round((double) originalMinutes * applicableChargeableDays / totalChargeableDays), 0);
    }

    LocalDate getCurrentBerlinDate() {
        return LocalDate.now(ZoneId.of("Europe/Berlin"));
    }

    @Transactional
    public void rebuildAllUserBalancesOnce() {
        logger.info("Starte Saldo-Neuberechnung für alle User...");
        userRepository.findAll().forEach(this::rebuildUserBalance);
        logger.info("✅ Saldo-Neuberechnung für alle User abgeschlossen.");
    }

    public List<TimeTrackingEntry> getEntriesForUser(User user, LocalDateTime start, LocalDateTime end) {
        return timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(user, start, end);
    }

    public int getWeeklyBalance(User user, LocalDate monday) {
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found: " + user.getUsername()));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);
        LocalDate weekEnd = monday.plusDays(6);
        LocalDate evaluationEnd = weekEnd.isAfter(getCurrentBerlinDate()) ? getCurrentBerlinDate() : weekEnd;
        if (monday.isAfter(evaluationEnd)) {
            return 0;
        }
        LocalDateTime startOfWeekDateTime = monday.atStartOfDay();
        LocalDateTime endOfWeekDateTime = evaluationEnd.plusDays(1).atStartOfDay();
        List<TimeTrackingEntry> entriesForWeek = timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(freshUser, startOfWeekDateTime, endOfWeekDateTime);
        Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate = entriesForWeek.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

        int sum = 0;
        Map<LocalDate, User> effectiveUserCache = new HashMap<>();
        LocalDate currentDate = monday;
        while (!currentDate.isAfter(evaluationEnd)) {
            User effectiveUser = resolveEffectiveUserForBalanceDate(freshUser, currentDate, effectiveUserCache);
            if (Boolean.TRUE.equals(effectiveUser.getIsPercentage())) {
                LocalDate segmentEnd = findHistoricalBalanceSegmentEnd(freshUser, currentDate, evaluationEnd, effectiveUserCache);
                sum += computeWeeklyWorkDifferenceForPercentageUserInRange(
                        effectiveUser,
                        monday,
                        currentDate,
                        segmentEnd,
                        approvedVacations,
                        entriesGroupedByDate
                );
                currentDate = segmentEnd.plusDays(1);
            } else {
                List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(currentDate, Collections.emptyList())
                        .stream()
                        .sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp))
                        .collect(Collectors.toList());
                sum += computeDailyWorkDifference(effectiveUser, currentDate, approvedVacations, entriesForDay);
                currentDate = currentDate.plusDays(1);
            }
        }
        return sum;
    }

    @Transactional
    public String updateDayTimeEntries(String targetUsername, String dateStr, List<TimeTrackingEntryDTO> updatedEntriesDTO, String adminUsername) {
        User targetUser = loadUserByUsername(targetUsername);
        User performingAdmin = loadUserByUsername(adminUsername);
        LocalDate date = LocalDate.parse(dateStr);

        boolean isSuperAdmin = performingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin && (performingAdmin.getCompany() == null || targetUser.getCompany() == null || !performingAdmin.getCompany().getId().equals(targetUser.getCompany().getId()))) {
            throw new SecurityException("Admin " + adminUsername + " ist nicht berechtigt, Benutzer " + targetUsername + " zu bearbeiten.");
        }

        List<TimeTrackingEntry> existingEntries = timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(targetUser, date);
        timeTrackingEntryRepository.deleteAll(existingEntries); // Alte Einträge für diesen Tag löschen

        updatedEntriesDTO.sort(Comparator.comparing(TimeTrackingEntryDTO::getEntryTimestamp));

        LocalDateTime previousTimestamp = null;
        TimeTrackingEntry.PunchType previousPunchType = null;

        for (TimeTrackingEntryDTO dto : updatedEntriesDTO) {
            if (dto.getEntryTimestamp() == null || dto.getPunchType() == null) {
                throw new IllegalArgumentException("Ungültiger Eintrag im DTO für User " + targetUsername + " am " + date + ": Zeitstempel oder Typ fehlt.");
            }
            // Validierung der Sequenz und Zeit
            if (previousTimestamp != null && dto.getEntryTimestamp().isBefore(previousTimestamp)) {
                throw new IllegalArgumentException("Zeitstempel müssen in chronologischer Reihenfolge sein. Fehler bei " + dto.getEntryTimestamp() + " nach " + previousTimestamp);
            }
            if (previousPunchType != null && dto.getPunchType() == previousPunchType) {
                throw new IllegalArgumentException("START und ENDE Stempel müssen sich abwechseln. Doppelter Typ: " + dto.getPunchType() + " um " + dto.getEntryTimestamp());
            }

            TimeTrackingEntry newEntry = new TimeTrackingEntry(
                    targetUser, dto.getEntryTimestamp(), dto.getPunchType(),
                    TimeTrackingEntry.PunchSource.ADMIN_CORRECTION); // Quelle ist Admin-Korrektur
            newEntry.setCorrectedByUser(true);
            newEntry.setSystemGeneratedNote(dto.getSystemGeneratedNote()); // Falls eine Notiz mitkommt
            timeTrackingEntryRepository.save(newEntry);
            previousTimestamp = dto.getEntryTimestamp();
            previousPunchType = dto.getPunchType();
        }

        rebuildUserBalance(targetUser);
        logger.info("Zeiteinträge für User {} am {} durch Admin {} aktualisiert (Sequenz überschrieben).", targetUsername, date, adminUsername);
        return "Zeiteinträge erfolgreich aktualisiert.";
    }

    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        User user = loadUserByUsername(username);
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        List<TimeReportDTO> reportDTOs = new ArrayList<>();

        Map<LocalDate, List<TimeTrackingEntry>> groupedByDate = timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(user, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                .stream().filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<TimeTrackingEntry> dailyEntries = groupedByDate.getOrDefault(date, Collections.emptyList())
                    .stream()
                    .sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp))
                    .collect(Collectors.toList());
            DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(dailyEntries, user, date);
            DailyTimeSummaryDTO.PrimaryTimes pt = summary.getPrimaryTimes();

            String workStart = pt.getFirstStartTime() != null ? pt.getFirstStartTime().format(timeFormatter) : "-";
            String workEnd = pt.getLastEndTime() != null ? pt.getLastEndTime().format(timeFormatter) : (pt.isOpen() ? "OFFEN" : "-");

            String breakStartStr = "-";
            String breakEndStr = "-";
            LocalDateTime lastTs = null;
            TimeTrackingEntry.PunchType lastType = null;
            boolean breakFound = false;
            for (TimeTrackingEntry entry : dailyEntries) { // Muss aufsteigend sortiert sein
                if (lastType == TimeTrackingEntry.PunchType.ENDE && entry.getPunchType() == TimeTrackingEntry.PunchType.START && !breakFound) {
                    if (lastTs != null) breakStartStr = lastTs.toLocalTime().format(timeFormatter); // Zeit des letzten ENDE
                    breakEndStr = entry.getEntryTimestamp().toLocalTime().format(timeFormatter); // Zeit des aktuellen START
                    breakFound = true;
                }
                lastTs = entry.getEntryTimestamp();
                lastType = entry.getPunchType();
            }

            reportDTOs.add(new TimeReportDTO(
                    user.getUsername(), date.format(dateFormatter), workStart,
                    breakStartStr, breakEndStr, workEnd,
                    dailyEntries.stream().map(TimeTrackingEntry::getSystemGeneratedNote).filter(Objects::nonNull).collect(Collectors.joining(" ")) // Kombinierte Notizen
            ));
        }
        return reportDTOs;
    }

    @Transactional
    public void deleteTimeTrackingEntriesByUser(User user) {
        logger.info("Lösche alle TimeTrackingEntry für Benutzer '{}'.", user.getUsername());
        timeTrackingEntryRepository.deleteByUser(user);
    }

    private LocalDateTime parseTimestampSafe(String timestampStr, int rowNum, String username, List<String> errors) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            errors.add("Zeile " + rowNum + " (User: " + username + "): Zeitstempel fehlt.");
            return null;
        }
        // Definiere eine Liste von unterstützten Formatierern
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME, // yyyy-MM-ddTHH:mm:ss oder yyyy-MM-ddTHH:mm
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (DateTimeParseException e) {
                // Ignoriere und versuche den nächsten Formatierer
            }
        }
        // Wenn kein Formatierer passt
        logger.warn("Ungültiges Zeitstempelformat in Zeile {}: '{}'. User: {}.", rowNum, timestampStr, username);
        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Zeitstempelformat '" + timestampStr + "'. Unterstützte Formate z.B. yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss, dd.MM.yyyy HH:mm.");
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        DataFormatter formatter = new DataFormatter();
        // Behandle Datumszellen speziell, um sie im ISO-Format zu bekommen, falls möglich
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                LocalDateTime date = cell.getLocalDateTimeCellValue();
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // oder ein anderes Standardformat
            } catch (Exception e) {
                // Fallback, wenn Konvertierung fehlschlägt
                return formatter.formatCellValue(cell).trim();
            }
        }
        return formatter.formatCellValue(cell).trim();
    }

    @Transactional
    public Map<String, Object> importTimeTrackingFromExcel(InputStream inputStream, Long adminCompanyId) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        List<Map<String, String>> invalidRows = new ArrayList<>();
        int importedCount = 0;
        Set<User> affectedUsers = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            int rowNum = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNum++;

                Map<String, String> rowData = new LinkedHashMap<>();

                try {
                    String username = getCellValueAsString(row.getCell(0));
                    String timestampStr = getCellValueAsString(row.getCell(1));
                    String punchTypeStr = getCellValueAsString(row.getCell(2));
                    String sourceStr = getCellValueAsString(row.getCell(3));
                    String note = getCellValueAsString(row.getCell(4));

                    rowData.put("rowNumber", String.valueOf(rowNum));
                    rowData.put("username", username);
                    rowData.put("timestamp", timestampStr);
                    rowData.put("punchType", punchTypeStr);
                    rowData.put("source", sourceStr);
                    rowData.put("note", note);

                    if (username == null || username.trim().isEmpty()) {
                        String msg = "Zeile " + rowNum + ": Username fehlt.";
                        errors.add(msg);
                        rowData.put("error", msg);
                        invalidRows.add(rowData);
                        continue;
                    }
                    Optional<User> userOpt = userRepository.findByUsername(username);
                    if (userOpt.isEmpty()) {
                        String msg = "Zeile " + rowNum + ": User '" + username + "' nicht gefunden.";
                        errors.add(msg);
                        rowData.put("error", msg);
                        invalidRows.add(rowData);
                        continue;
                    }
                    User user = userOpt.get();

                    if (adminCompanyId != null) { // Nur prüfen, wenn der Admin einer Firma zugeordnet ist
                        User importingAdmin = userRepository.findAll().stream() // Ineffizient, besser Admin direkt übergeben oder laden
                                .filter(u -> u.getCompany() != null && u.getCompany().getId().equals(adminCompanyId) && u.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN")))
                                .findFirst().orElse(null);
                        boolean isSuperAdmin = importingAdmin != null && importingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

                        if (!isSuperAdmin && (user.getCompany() == null || !user.getCompany().getId().equals(adminCompanyId))) {
                            String msg = "Zeile " + rowNum + ": User '" + username + "' gehört nicht zur Firma des importierenden Admins oder Admin ist keiner Firma zugeordnet.";
                            errors.add(msg);
                            rowData.put("error", msg);
                            invalidRows.add(rowData);
                            continue;
                        }
                    }


                    LocalDateTime entryTimestamp = parseTimestampSafe(timestampStr, rowNum, username, errors);
                    if (entryTimestamp == null) {
                        rowData.putIfAbsent("error", errors.get(errors.size() - 1));
                        invalidRows.add(rowData);
                        continue;
                    }

                    if (punchTypeStr == null || punchTypeStr.trim().isEmpty()) {
                        String msg = "Zeile " + rowNum + " (User: " + username + "): PunchType fehlt.";
                        errors.add(msg);
                        rowData.put("error", msg);
                        invalidRows.add(rowData);
                        continue;
                    }
                    TimeTrackingEntry.PunchType punchType;
                    try {
                        punchType = TimeTrackingEntry.PunchType.valueOf(punchTypeStr.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        String msg = "Zeile " + rowNum + " (User: " + username + "): Ungültiger PunchType '" + punchTypeStr + "'. Erwartet: START oder ENDE.";
                        errors.add(msg);
                        rowData.put("error", msg);
                        invalidRows.add(rowData);
                        continue;
                    }

                    TimeTrackingEntry.PunchSource source = TimeTrackingEntry.PunchSource.MANUAL_IMPORT;
                    if (sourceStr != null && !sourceStr.trim().isEmpty()) {
                        try {
                            source = TimeTrackingEntry.PunchSource.valueOf(sourceStr.trim().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            String msg = "Zeile " + rowNum + " (User: " + username + "): Ungültige Source '" + sourceStr + "'. Wird auf MANUAL_IMPORT gesetzt.";
                            errors.add(msg);
                            rowData.put("error", msg);
                            invalidRows.add(rowData);
                            }
                    }

                    TimeTrackingEntry newEntry = new TimeTrackingEntry(user, entryTimestamp, punchType, source);
                    if (note != null && !note.trim().isEmpty()) {
                        newEntry.setSystemGeneratedNote(note);
                    }
                    newEntry.setCorrectedByUser(true);

                    timeTrackingEntryRepository.save(newEntry);
                    affectedUsers.add(user);
                    successes.add("Zeile " + rowNum + ": Eintrag für User '" + username + "' am " + entryTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " ("+punchType+") importiert.");
                    importedCount++;

                } catch (Exception e) {
                    logger.error("Fehler beim Verarbeiten der Excel-Zeile {}: {}", rowNum, e.getMessage(), e);
                    String msg = "Zeile " + rowNum + ": Unerwarteter Fehler - " + e.getMessage();
                    errors.add(msg);
                    rowData.put("error", msg);
                    invalidRows.add(rowData);
                }
            }

            for (User user : affectedUsers) {
                rebuildUserBalance(user);
            }

        } catch (Exception e) {
            logger.error("Fehler beim Import der Excel-Datei: {}", e.getMessage(), e);
            errors.add("Genereller Fehler beim Lesen oder Verarbeiten der Excel-Datei: " + e.getMessage());
        }

        result.put("importedCount", importedCount);
        result.put("successMessages", successes);
        result.put("errorMessages", errors);
        result.put("invalidRows", invalidRows);
        return result;
    }

    @Transactional
    public Map<String, Object> importTimeTrackingFromRows(List<TimeTrackingImportRowDTO> rows, Long adminCompanyId) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        List<Map<String, String>> invalidRows = new ArrayList<>();
        int importedCount = 0;
        Set<User> affectedUsers = new HashSet<>();

        int rowNum = 1;
        for (TimeTrackingImportRowDTO row : rows) {
            Map<String, String> rowData = new LinkedHashMap<>();
            String username = row.getUsername();
            String timestampStr = row.getTimestamp();
            String punchTypeStr = row.getPunchType();
            String sourceStr = row.getSource();
            String note = row.getNote();

            rowData.put("rowNumber", String.valueOf(rowNum));
            rowData.put("username", username);
            rowData.put("timestamp", timestampStr);
            rowData.put("punchType", punchTypeStr);
            rowData.put("source", sourceStr);
            rowData.put("note", note);

            if (username == null || username.trim().isEmpty()) {
                String msg = "Zeile " + rowNum + ": Username fehlt.";
                errors.add(msg);
                rowData.put("error", msg);
                invalidRows.add(rowData);
                rowNum++;
                continue;
            }

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                String msg = "Zeile " + rowNum + ": User '" + username + "' nicht gefunden.";
                errors.add(msg);
                rowData.put("error", msg);
                invalidRows.add(rowData);
                rowNum++;
                continue;
            }
            User user = userOpt.get();

            if (adminCompanyId != null) {
                User importingAdmin = userRepository.findAll().stream()
                        .filter(u -> u.getCompany() != null && u.getCompany().getId().equals(adminCompanyId)
                                && u.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN")))
                        .findFirst().orElse(null);
                boolean isSuperAdmin = importingAdmin != null && importingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

                if (!isSuperAdmin && (user.getCompany() == null || !user.getCompany().getId().equals(adminCompanyId))) {
                    String msg = "Zeile " + rowNum + ": User '" + username + "' gehört nicht zur Firma des importierenden Admins oder Admin ist keiner Firma zugeordnet.";
                    errors.add(msg);
                    rowData.put("error", msg);
                    invalidRows.add(rowData);
                    rowNum++;
                    continue;
                }
            }

            LocalDateTime entryTimestamp = parseTimestampSafe(timestampStr, rowNum, username, errors);
            if (entryTimestamp == null) {
                rowData.putIfAbsent("error", errors.get(errors.size() - 1));
                invalidRows.add(rowData);
                rowNum++;
                continue;
            }

            if (punchTypeStr == null || punchTypeStr.trim().isEmpty()) {
                String msg = "Zeile " + rowNum + " (User: " + username + "): PunchType fehlt.";
                errors.add(msg);
                rowData.put("error", msg);
                invalidRows.add(rowData);
                rowNum++;
                continue;
            }

            TimeTrackingEntry.PunchType punchType;
            try {
                punchType = TimeTrackingEntry.PunchType.valueOf(punchTypeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                String msg = "Zeile " + rowNum + " (User: " + username + "): Ungültiger PunchType '" + punchTypeStr + "'. Erwartet: START oder ENDE.";
                errors.add(msg);
                rowData.put("error", msg);
                invalidRows.add(rowData);
                rowNum++;
                continue;
            }

            TimeTrackingEntry.PunchSource source = TimeTrackingEntry.PunchSource.MANUAL_IMPORT;
            if (sourceStr != null && !sourceStr.trim().isEmpty()) {
                try {
                    source = TimeTrackingEntry.PunchSource.valueOf(sourceStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    String msg = "Zeile " + rowNum + " (User: " + username + "): Ungültige Source '" + sourceStr + "'. Wird auf MANUAL_IMPORT gesetzt.";
                    errors.add(msg);
                    rowData.put("error", msg);
                    invalidRows.add(rowData);
                }
            }

            try {
                TimeTrackingEntry newEntry = new TimeTrackingEntry(user, entryTimestamp, punchType, source);
                if (note != null && !note.trim().isEmpty()) {
                    newEntry.setSystemGeneratedNote(note);
                }
                newEntry.setCorrectedByUser(true);
                timeTrackingEntryRepository.save(newEntry);
                affectedUsers.add(user);
                successes.add("Zeile " + rowNum + ": Eintrag für User '" + username + "' am " + entryTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " (" + punchType + ") importiert.");
                importedCount++;
            } catch (Exception e) {
                String msg = "Zeile " + rowNum + ": Unerwarteter Fehler - " + e.getMessage();
                errors.add(msg);
                rowData.put("error", msg);
                invalidRows.add(rowData);
            }

            rowNum++;
        }

        for (User user : affectedUsers) {
            rebuildUserBalance(user);
        }

        result.put("importedCount", importedCount);
        result.put("successMessages", successes);
        result.put("errorMessages", errors);
        result.put("invalidRows", invalidRows);
        return result;
    }

    @Transactional
    public TimeTrackingEntryDTO updateEntryCustomer(Long id, Long customerId, String requestingUser) {
        User requester = loadUserByUsername(requestingUser);
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found: " + id));
        User entryUser = entry.getUser();
        boolean allowed = requester.getId().equals(entryUser.getId()) ||
                requester.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                (requester.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                        requester.getCompany() != null && entryUser.getCompany() != null &&
                        requester.getCompany().getId().equals(entryUser.getCompany().getId()));
        if (!allowed) {
            throw new SecurityException("Not allowed");
        }
        if (entryUser.getCompany() == null || !Boolean.TRUE.equals(entryUser.getCompany().getCustomerTrackingEnabled())) {
            throw new IllegalStateException("Feature disabled");
        }
        Customer customer = null;
        if (customerId != null) {
            customer = resolveCustomerForUser(customerId, entryUser);
        }
        entry.setCustomer(customer);
        TimeTrackingEntry saved = timeTrackingEntryRepository.save(entry);

        // update last customer if this was the most recent entry
        List<TimeTrackingEntry> latestEntries = timeTrackingEntryRepository
                .findByUserOrderByEntryTimestampDesc(entryUser);
        if (!latestEntries.isEmpty() && Objects.equals(latestEntries.get(0).getId(), saved.getId())) {
            entryUser.setLastCustomer(saved.getCustomer());
            userRepository.save(entryUser);
        }

        return TimeTrackingEntryDTO.fromEntity(saved);
    }

    @Transactional
    public TimeTrackingEntryDTO updateEntryProject(Long id, Long projectId, String requestingUser) {
        User requester = loadUserByUsername(requestingUser);
        TimeTrackingEntry entry = timeTrackingEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found: " + id));
        User entryUser = entry.getUser();
        boolean allowed = requester.getId().equals(entryUser.getId()) ||
                requester.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                (requester.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                        requester.getCompany() != null && entryUser.getCompany() != null &&
                        requester.getCompany().getId().equals(entryUser.getCompany().getId()));
        if (!allowed) {
            throw new SecurityException("Not allowed");
        }
        if (entryUser.getCompany() == null || !Boolean.TRUE.equals(entryUser.getCompany().getCustomerTrackingEnabled())) {
            throw new IllegalStateException("Feature disabled");
        }
        Project project = null;
        if (projectId != null) {
            project = resolveProjectForUser(projectId, entryUser);
        }
        entry.setProject(project);
        TimeTrackingEntry saved = timeTrackingEntryRepository.save(entry);
        return TimeTrackingEntryDTO.fromEntity(saved);
    }

    public List<Customer> getRecentCustomers(String username) {
        User user = loadUserByUsername(username);
        List<Long> ids = timeTrackingEntryRepository.findRecentCustomerIds(user.getId(), org.springframework.data.domain.PageRequest.of(0,5));
        if (ids.isEmpty()) return Collections.emptyList();
        return customerRepository.findAllById(ids);
    }

    @Transactional
    public void assignCustomerForDay(String username, LocalDate date, Long customerId) {
        User user = loadUserByUsername(username);
        if (user.getCompany() == null || !Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            throw new IllegalStateException("Feature disabled");
        }
        Customer customer = resolveCustomerForUser(customerId, user);
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date);
        for (TimeTrackingEntry e : entries) {
            e.setCustomer(customer);
        }
        timeTrackingEntryRepository.saveAll(entries);

        // update last customer if latest entry falls on this day
        TimeTrackingEntry latest = timeTrackingEntryRepository
                .findByUserOrderByEntryTimestampDesc(user)
                .stream().findFirst().orElse(null);
        if (latest != null && date.equals(latest.getEntryDate())) {
            user.setLastCustomer(customer);
            userRepository.save(user);
        }
    }

    @Transactional
    public void assignProjectForDay(String username, LocalDate date, Long projectId) {
        User user = loadUserByUsername(username);
        if (user.getCompany() == null || !Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            throw new IllegalStateException("Feature disabled");
        }
        Project project = resolveProjectForUser(projectId, user);
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date);
        for (TimeTrackingEntry e : entries) {
            e.setProject(project);
        }
        timeTrackingEntryRepository.saveAll(entries);
    }

    @Transactional
    public void assignCustomerForTimeRange(String username, LocalDate date, LocalTime startTime, LocalTime endTime, Long customerId) {
        User user = loadUserByUsername(username);
        if (user.getCompany() == null || !Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            throw new IllegalStateException("Feature disabled");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime required");
        }
        if (endTime.isBefore(startTime)) {
            LocalTime tmp = startTime;
            startTime = endTime;
            endTime = tmp;
        }
        Customer customer = resolveCustomerForUser(customerId, user);
        LocalDateTime startDt = LocalDateTime.of(date, startTime);
        LocalDateTime endDt = LocalDateTime.of(date, endTime);
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(user, startDt, endDt);
        for (TimeTrackingEntry e : entries) {
            e.setCustomer(customer);
        }
        timeTrackingEntryRepository.saveAll(entries);

        TimeTrackingEntry latest = timeTrackingEntryRepository
                .findByUserOrderByEntryTimestampDesc(user)
                .stream().findFirst().orElse(null);
        if (latest != null && !entries.isEmpty() && latest.getId().equals(entries.get(entries.size() - 1).getId())) {
            user.setLastCustomer(customer);
            userRepository.save(user);
        }
    }

    @Transactional
    public void saveDailyNote(String username, LocalDate date, String note) {
        User user = loadUserByUsername(username);
        DailyNote dailyNote = dailyNoteRepository.findByUserAndNoteDate(user, date).orElse(null);
        if (dailyNote == null) {
            dailyNote = new DailyNote();
            dailyNote.setUser(user);
            dailyNote.setNoteDate(date);
        }
        dailyNote.setContent(note);
        dailyNoteRepository.save(dailyNote);
    }

    public String getDailyNoteContent(User user, LocalDate date) {
        return dailyNoteRepository.findByUserAndNoteDate(user, date)
                .map(DailyNote::getContent)
                .orElse(null);
    }
}
