package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);

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

    private User loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    @Transactional
    public TimeTrackingEntryDTO handlePunch(String username, TimeTrackingEntry.PunchSource source) {
        User user = loadUserByUsername(username);
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Zurich"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Zurich")).truncatedTo(ChronoUnit.MINUTES);

        Optional<TimeTrackingEntry> lastEntryOpt = timeTrackingEntryRepository.findLastEntryByUserAndDate(user, today);

        TimeTrackingEntry.PunchType nextPunchType;
        if (lastEntryOpt.isEmpty()) {
            nextPunchType = TimeTrackingEntry.PunchType.START;
        } else {
            nextPunchType = (lastEntryOpt.get().getPunchType() == TimeTrackingEntry.PunchType.START) ?
                            TimeTrackingEntry.PunchType.ENDE : TimeTrackingEntry.PunchType.START;
        }

        TimeTrackingEntry newEntry = new TimeTrackingEntry(user, now, nextPunchType, source);
        if (nextPunchType == TimeTrackingEntry.PunchType.ENDE && source == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END) {
            newEntry.setSystemGeneratedNote("Automatischer Arbeitsende-Stempel. Bitte korrigieren.");
        }

        TimeTrackingEntry savedEntry = timeTrackingEntryRepository.save(newEntry);
        logger.info("User '{}' punched {}. Timestamp: {}, Source: {}", username, nextPunchType, now, source);
        rebuildUserBalance(user);
        return TimeTrackingEntryDTO.fromEntity(savedEntry);
    }

    @Transactional
    public void autoEndDayForUsersWhoForgotPunchOut(LocalDate date) {
        List<User> usersToProcess = timeTrackingEntryRepository.findUsersWithLastEntryAsStartOnDate(date);
        LocalDateTime autoEndTime = date.atTime(23, 20);

        for (User user : usersToProcess) {
             User freshUser = userRepository.findById(user.getId()).orElse(null);
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
            rebuildUserBalance(user);
        }
    }

    public DailyTimeSummaryDTO getDailySummary(String username, LocalDate date) {
        User user = loadUserByUsername(username);
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date);
        return calculateDailySummaryFromEntries(entries, user, date);
    }
    
    public List<TimeTrackingEntry> getTimeTrackingEntriesForUserAndDate(User user, LocalDate date) {
        return timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date);
    }


    public List<DailyTimeSummaryDTO> getUserHistory(String username) {
        User user = loadUserByUsername(username);
        List<TimeTrackingEntry> allEntries = timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(user);

        Map<LocalDate, List<TimeTrackingEntry>> entriesByDate = allEntries.stream()
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate, () -> new TreeMap<>(Comparator.reverseOrder()), Collectors.toList()));

        return entriesByDate.entrySet().stream()
                .map(entry -> {
                    List<TimeTrackingEntry> dailyEntriesSortedAsc = entry.getValue().stream()
                            .sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp))
                            .collect(Collectors.toList());
                    return calculateDailySummaryFromEntries(dailyEntriesSortedAsc, user, entry.getKey());
                })
                .collect(Collectors.toList());
    }
    
    private DailyTimeSummaryDTO.PrimaryTimes getPrimaryPunchTimes(List<TimeTrackingEntry> entries) {
        LocalTime firstStart = null;
        LocalTime lastEnd = null;
        boolean isOpen = false;

        if (entries != null && !entries.isEmpty()) {
            Optional<TimeTrackingEntry> firstStartEntry = entries.stream()
                .filter(e -> e.getPunchType() == TimeTrackingEntry.PunchType.START)
                .findFirst();
            if (firstStartEntry.isPresent()) {
                firstStart = firstStartEntry.get().getEntryTime();
            }

            Optional<TimeTrackingEntry> lastEndEntry = entries.stream()
                .filter(e -> e.getPunchType() == TimeTrackingEntry.PunchType.ENDE)
                .reduce((first, second) -> second);
            if (lastEndEntry.isPresent()) {
                 lastEnd = lastEndEntry.get().getEntryTime();
            }

            TimeTrackingEntry lastOverallEntry = entries.get(entries.size() - 1);
            if (lastOverallEntry.getPunchType() == TimeTrackingEntry.PunchType.START) {
                isOpen = true;
                 if(lastEndEntry.isPresent() && lastOverallEntry.getEntryTimestamp().isBefore(lastEndEntry.get().getEntryTimestamp())){
                    lastEnd = null; 
                }
            }
             if (firstStart != null && lastEnd != null && lastEnd.isBefore(firstStart) && !isOpen) {
                lastEnd = null;
            }


        }
        return new DailyTimeSummaryDTO.PrimaryTimes(firstStart, lastEnd, isOpen);
    }


    private DailyTimeSummaryDTO calculateDailySummaryFromEntries(List<TimeTrackingEntry> entries, User user, LocalDate date) {
        Duration totalWorkTime = Duration.ZERO;
        Duration totalBreakTime = Duration.ZERO;
        LocalDateTime lastStartTime = null;
        LocalDateTime lastWorkEndTime = null;
        List<TimeTrackingEntryDTO> entryDTOs = entries.stream().map(TimeTrackingEntryDTO::fromEntity).collect(Collectors.toList());
        String dailyNoteContent = null;

        for (TimeTrackingEntry entry : entries) {
            if (entry.getPunchType() == TimeTrackingEntry.PunchType.START) {
                if (lastWorkEndTime != null) {
                    totalBreakTime = totalBreakTime.plus(Duration.between(lastWorkEndTime, entry.getEntryTimestamp()));
                }
                lastStartTime = entry.getEntryTimestamp();
                lastWorkEndTime = null;
            } else if (entry.getPunchType() == TimeTrackingEntry.PunchType.ENDE) {
                if (lastStartTime != null) {
                    totalWorkTime = totalWorkTime.plus(Duration.between(lastStartTime, entry.getEntryTimestamp()));
                    lastWorkEndTime = entry.getEntryTimestamp();
                } else {
                    logger.warn("User {} am {}: ENDE-Stempel um {} ohne vorherigen START-Stempel gefunden. Dieser wird ignoriert.", user.getUsername(), date, entry.getEntryTimestamp());
                }
                lastStartTime = null;
            }
        }
        
        boolean needsCorrection = entries.stream()
                .anyMatch(e -> e.getSource() == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END && !e.isCorrectedByUser());

        return new DailyTimeSummaryDTO(
                date,
                (int) totalWorkTime.toMinutes(),
                (int) totalBreakTime.toMinutes(),
                entryDTOs,
                dailyNoteContent,
                needsCorrection,
                getPrimaryPunchTimes(entries)
        );
    }

    @Transactional
    public void rebuildUserBalance(User user) {
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + user.getId()));
        logger.debug("Entering rebuildUserBalance for user: '{}'.", freshUser.getUsername());

        List<TimeTrackingEntry> allEntriesForUser = timeTrackingEntryRepository.findByUserOrderByEntryTimestampDesc(freshUser);
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);

        if (freshUser.getIsHourly() == null) freshUser.setIsHourly(false);
        if (freshUser.getIsPercentage() == null) freshUser.setIsPercentage(false);
        if (freshUser.getTrackingBalanceInMinutes() == null) freshUser.setTrackingBalanceInMinutes(0);

        if (allEntriesForUser.isEmpty() && approvedVacations.isEmpty() && sickLeaveRepository.findByUser(freshUser).isEmpty()) {
            freshUser.setTrackingBalanceInMinutes(0);
            userRepository.save(freshUser);
            logger.info("Saldo für {} auf 0 gesetzt (keine Einträge/Abwesenheiten).", freshUser.getUsername());
            return;
        }

        LocalDate firstDayToConsider = LocalDate.now(ZoneId.of("Europe/Zurich"));
        Optional<LocalDate> firstTrackingDayOpt = allEntriesForUser.stream().map(TimeTrackingEntry::getEntryDate).filter(Objects::nonNull).min(LocalDate::compareTo);
        Optional<LocalDate> firstVacationDayOpt = approvedVacations.stream().map(VacationRequest::getStartDate).min(LocalDate::compareTo);
        Optional<LocalDate> firstSickLeaveDayOpt = sickLeaveRepository.findByUser(freshUser).stream().map(com.chrono.chrono.entities.SickLeave::getStartDate).min(LocalDate::compareTo);

        firstDayToConsider = Stream.of(firstTrackingDayOpt, firstVacationDayOpt, firstSickLeaveDayOpt)
            .filter(Optional::isPresent).map(Optional::get).min(LocalDate::compareTo).orElse(firstDayToConsider);
        
        LocalDate lastDay = LocalDate.now(ZoneId.of("Europe/Zurich"));
        Optional<LocalDate> lastTrackingDayOpt = allEntriesForUser.stream().map(TimeTrackingEntry::getEntryDate).filter(Objects::nonNull).max(LocalDate::compareTo);
        if(lastTrackingDayOpt.isPresent() && lastTrackingDayOpt.get().isAfter(lastDay)){
            lastDay = lastTrackingDayOpt.get();
        }

        int totalMinutesBalance = 0;
        if (!firstDayToConsider.isAfter(lastDay)) {
            logger.info("Saldo-Neuberechnung für {}: Zeitraum {} bis {}", freshUser.getUsername(), firstDayToConsider, lastDay);
            Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate = allEntriesForUser.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

            if (Boolean.TRUE.equals(freshUser.getIsPercentage())) {
                LocalDate currentWeekStart = firstDayToConsider.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!currentWeekStart.isAfter(lastDay)) {
                    totalMinutesBalance += computeWeeklyWorkDifferenceForPercentageUser(freshUser, currentWeekStart, lastDay, approvedVacations, entriesGroupedByDate);
                    currentWeekStart = currentWeekStart.plusWeeks(1);
                }
            } else {
                for (LocalDate d = firstDayToConsider; !d.isAfter(lastDay); d = d.plusDays(1)) {
                    List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(d, Collections.emptyList())
                            .stream().sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp)).collect(Collectors.toList());
                    totalMinutesBalance += computeDailyWorkDifference(freshUser, d, approvedVacations, entriesForDay);
                }
            }
            freshUser.setTrackingBalanceInMinutes(totalMinutesBalance);
            logger.info("Saldo für {} neu berechnet: {} Minuten.", freshUser.getUsername(), totalMinutesBalance);
        } else {
            logger.info("Kein relevanter Zeitraum für Saldo-Neuberechnung für {}.", freshUser.getUsername());
        }
        userRepository.save(freshUser);
    }

    private int getWorkedMinutesForDate(User user, LocalDate date, Map<LocalDate, List<TimeTrackingEntry>> allUserEntriesGroupedByDate) {
        List<TimeTrackingEntry> entriesForDay = allUserEntriesGroupedByDate.getOrDefault(date, Collections.emptyList())
            .stream().sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp)).collect(Collectors.toList());
        return entriesForDay.isEmpty() ? 0 : calculateDailySummaryFromEntries(entriesForDay, user, date).getWorkedMinutes();
    }

    private int computeWeeklyWorkDifferenceForPercentageUser(User user, LocalDate weekStart, LocalDate lastDayOverall, List<VacationRequest> allApprovedVacationsForUser, Map<LocalDate, List<TimeTrackingEntry>> allUserEntriesGroupedByDate) {
        int totalWorkedMinutesInWeek = 0;
        LocalDate endOfWeek = weekStart.plusDays(6);
        for (LocalDate d = weekStart; !d.isAfter(endOfWeek) && !d.isAfter(lastDayOverall); d = d.plusDays(1)) {
            totalWorkedMinutesInWeek += getWorkedMinutesForDate(user, d, allUserEntriesGroupedByDate);
        }
        List<VacationRequest> approvedVacationsInThisWeek = allApprovedVacationsForUser.stream()
                .filter(vr -> vr.isApproved() && !vr.getEndDate().isBefore(weekStart) && !vr.getStartDate().isAfter(endOfWeek))
                .collect(Collectors.toList());
        int expectedWeeklyMinutesAdjusted = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, weekStart, approvedVacationsInThisWeek);
        return totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjusted;
    }

    public int computeDailyWorkDifference(User user, LocalDate date, List<VacationRequest> approvedVacations, List<TimeTrackingEntry> entriesForDay) {
        int workedMinutes = calculateDailySummaryFromEntries(entriesForDay, user, date).getWorkedMinutes();
        int adjustedExpectedMinutes = workScheduleService.computeExpectedWorkMinutes(user, date, approvedVacations);
        return workedMinutes - adjustedExpectedMinutes;
    }
    
    @Transactional
    public void rebuildAllUserBalancesOnce() {
        logger.info("Starte Saldo-Neuberechnung für alle User...");
        userRepository.findAll().forEach(this::rebuildUserBalance);
        logger.info("✅ Saldo-Neuberechnung für alle User abgeschlossen.");
    }

    public int getWeeklyBalance(User user, LocalDate monday) {
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);
        LocalDateTime startOfWeek = monday.atStartOfDay();
        LocalDateTime endOfWeekPlusOne = monday.plusDays(7).atStartOfDay();
        List<TimeTrackingEntry> entriesForWeek = timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(freshUser, startOfWeek, endOfWeekPlusOne);
        Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate = entriesForWeek.stream()
            .filter(e -> e.getEntryDate() != null)
            .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

        if (Boolean.TRUE.equals(freshUser.getIsPercentage())) {
            return computeWeeklyWorkDifferenceForPercentageUser(freshUser, monday, monday.plusDays(6), approvedVacations, entriesGroupedByDate);
        } else {
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                 LocalDate currentDate = monday.plusDays(i);
                 List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(currentDate, Collections.emptyList());
                sum += computeDailyWorkDifference(freshUser, currentDate, approvedVacations, entriesForDay);
            }
            return sum;
        }
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
        timeTrackingEntryRepository.deleteAll(existingEntries);

        updatedEntriesDTO.sort(Comparator.comparing(TimeTrackingEntryDTO::getEntryTimestamp));

        LocalDateTime previousTimestamp = null;
        TimeTrackingEntry.PunchType previousPunchType = null;

        for (TimeTrackingEntryDTO dto : updatedEntriesDTO) {
            if (dto.getEntryTimestamp() == null || dto.getPunchType() == null) {
                throw new IllegalArgumentException("Ungültiger Eintrag im DTO: Zeitstempel oder Typ fehlt.");
            }
            if (previousTimestamp != null && dto.getEntryTimestamp().isBefore(previousTimestamp)) {
                throw new IllegalArgumentException("Zeitstempel müssen in chronologischer Reihenfolge sein.");
            }
            if (previousPunchType != null && dto.getPunchType() == previousPunchType) {
                 throw new IllegalArgumentException("START und ENDE Stempel müssen sich abwechseln. Doppelter Typ: " + dto.getPunchType() + " um " + dto.getEntryTimestamp());
            }


            TimeTrackingEntry newEntry = new TimeTrackingEntry(
                    targetUser, dto.getEntryTimestamp(), dto.getPunchType(),
                    TimeTrackingEntry.PunchSource.ADMIN_CORRECTION);
            newEntry.setCorrectedByUser(true);
            newEntry.setSystemGeneratedNote(dto.getSystemGeneratedNote());
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
            List<TimeTrackingEntry> dailyEntries = groupedByDate.getOrDefault(date, Collections.emptyList());
            DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(dailyEntries, user, date);
            DailyTimeSummaryDTO.PrimaryTimes pt = summary.getPrimaryTimes();
            
            String workStart = pt.getFirstStartTime() != null ? pt.getFirstStartTime().format(timeFormatter) : "-";
            String workEnd = pt.getLastEndTime() != null ? pt.getLastEndTime().format(timeFormatter) : (pt.isOpen() ? "OFFEN" : "-");
            String breakStartStr = "-";
            String breakEndStr = "-";

            LocalDateTime lastTs = null;
            TimeTrackingEntry.PunchType lastType = null;
            boolean breakFound = false;
            for (TimeTrackingEntry entry : dailyEntries) {
                if (lastType == TimeTrackingEntry.PunchType.ENDE && entry.getPunchType() == TimeTrackingEntry.PunchType.START && !breakFound) {
                    breakStartStr = lastTs.toLocalTime().format(timeFormatter);
                    breakEndStr = entry.getEntryTimestamp().toLocalTime().format(timeFormatter);
                    breakFound = true; 
                }
                lastTs = entry.getEntryTimestamp();
                lastType = entry.getPunchType();
            }

            reportDTOs.add(new TimeReportDTO(
                    user.getUsername(), date.format(dateFormatter), workStart,
                    breakStartStr, breakEndStr, workEnd, summary.getDailyNote()));
        }
        return reportDTOs;
    }
    
    @Transactional
    public void deleteTimeTrackingEntriesByUser(User user) {
        logger.info("Lösche alle TimeTrackingEntry für Benutzer '{}'.", user.getUsername());
        timeTrackingEntryRepository.deleteByUser(user);
    }
}
