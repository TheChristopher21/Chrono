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
        rebuildUserBalance(user); // Saldo nach jedem Stempel neu berechnen
        return TimeTrackingEntryDTO.fromEntity(savedEntry);
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

    public DailyTimeSummaryDTO getDailySummary(String username, LocalDate date) {
        User user = loadUserByUsername(username);
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository.findByUserAndEntryDateOrderByEntryTimestampAsc(user, date);
        return calculateDailySummaryFromEntries(entries, user, date);
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
                    List<TimeTrackingEntry> dailyEntriesSortedAsc = entry.getValue().stream()
                            .sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp))
                            .collect(Collectors.toList());
                    return calculateDailySummaryFromEntries(dailyEntriesSortedAsc, user, entry.getKey());
                })
                .collect(Collectors.toList()); // Die resultierende Liste ist nach Datum absteigend sortiert wegen TreeMap
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
        Duration totalWorkTime = Duration.ZERO;
        Duration totalBreakTime = Duration.ZERO;
        LocalDateTime lastStartTime = null;
        LocalDateTime lastWorkEndTime = null;
        List<TimeTrackingEntryDTO> entryDTOs = entries.stream()
                .map(TimeTrackingEntryDTO::fromEntity)
                .collect(Collectors.toList());
        String dailyNoteContent = null;

        for (TimeTrackingEntry entry : entries) {
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

        boolean needsCorrection = entries.stream()
                .anyMatch(e -> e.getSource() == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END && !e.isCorrectedByUser());

        return new DailyTimeSummaryDTO(
                user.getUsername(),
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
        // Holt den frischesten Benutzerstatus aus der DB
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + user.getId()));

        // ====================== FINALE KORREKTUR FÜR STUNDENLÖHNER ======================
        if (Boolean.TRUE.equals(freshUser.getIsHourly())) {
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

        if (allEntriesForUser.isEmpty() && approvedVacations.isEmpty() && sickLeaveRepository.findByUser(freshUser).isEmpty()) {
            if (freshUser.getTrackingBalanceInMinutes() != 0) {
                logger.info("Saldo für {} auf 0 gesetzt (keine Zeiteinträge oder relevante Abwesenheiten). Alter Saldo war: {}", freshUser.getUsername(), freshUser.getTrackingBalanceInMinutes());
                freshUser.setTrackingBalanceInMinutes(0);
                userRepository.save(freshUser);
            } else {
                logger.info("Saldo für {} bereits 0 und keine Einträge/Abwesenheiten.", freshUser.getUsername());
            }
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
            } else { // Dieser Block ist für Standard-Mitarbeiter
                for (LocalDate d = firstDayToConsider; !d.isAfter(lastDay); d = d.plusDays(1)) {
                    List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(d, Collections.emptyList())
                            .stream().sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp)).collect(Collectors.toList());
                    int dailyDifference = computeDailyWorkDifference(freshUser, d, approvedVacations, entriesForDay);
                    totalMinutesBalance += dailyDifference;
                    if ("Chantale".equals(freshUser.getUsername()) || logger.isTraceEnabled()) { // Debugging
                        DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(entriesForDay, freshUser, d);
                        int expected = workScheduleService.computeExpectedWorkMinutes(freshUser, d, approvedVacations);
                        logger.info("[User: {}] Saldo Tag {}: Ist: {}min, Soll: {}min, Diff: {}min, Saldo bisher: {}min",
                                freshUser.getUsername(), d, summary.getWorkedMinutes(), expected, dailyDifference, totalMinutesBalance - dailyDifference);
                    }
                }
            }
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

        int weeklyDifference = totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjusted;
        logger.debug("User: {}, Woche ab: {}, Gearbeitet: {}min, Erwartet (bereinigt): {}min, Differenz diese Woche: {}min",
                user.getUsername(), weekStart, totalWorkedMinutesInWeek, expectedWeeklyMinutesAdjusted, weeklyDifference);
        return weeklyDifference;
    }

    public int computeDailyWorkDifference(User user, LocalDate date, List<VacationRequest> approvedVacations, List<TimeTrackingEntry> entriesForDay) {
        DailyTimeSummaryDTO summary = calculateDailySummaryFromEntries(entriesForDay, user, date);
        int workedMinutes = summary.getWorkedMinutes();
        // Die Methode im WorkScheduleService erwartet jetzt auch approvedVacations
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
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found: " + user.getUsername()));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);
        LocalDateTime startOfWeekDateTime = monday.atStartOfDay();
        LocalDateTime endOfWeekDateTime = monday.plusDays(7).atStartOfDay();
        List<TimeTrackingEntry> entriesForWeek = timeTrackingEntryRepository.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(freshUser, startOfWeekDateTime, endOfWeekDateTime);
        Map<LocalDate, List<TimeTrackingEntry>> entriesGroupedByDate = entriesForWeek.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(TimeTrackingEntry::getEntryDate));

        if (Boolean.TRUE.equals(freshUser.getIsPercentage())) {
            return computeWeeklyWorkDifferenceForPercentageUser(freshUser, monday, monday.plusDays(6), approvedVacations, entriesGroupedByDate);
        } else {
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                LocalDate currentDate = monday.plusDays(i);
                List<TimeTrackingEntry> entriesForDay = entriesGroupedByDate.getOrDefault(currentDate, Collections.emptyList())
                        .stream()
                        .sorted(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp))
                        .collect(Collectors.toList());
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

                try {
                    String username = getCellValueAsString(row.getCell(0));
                    String timestampStr = getCellValueAsString(row.getCell(1));
                    String punchTypeStr = getCellValueAsString(row.getCell(2));
                    String sourceStr = getCellValueAsString(row.getCell(3));
                    String note = getCellValueAsString(row.getCell(4));

                    if (username == null || username.trim().isEmpty()) {
                        errors.add("Zeile " + rowNum + ": Username fehlt.");
                        continue;
                    }
                    Optional<User> userOpt = userRepository.findByUsername(username);
                    if (userOpt.isEmpty()) {
                        errors.add("Zeile " + rowNum + ": User '" + username + "' nicht gefunden.");
                        continue;
                    }
                    User user = userOpt.get();

                    if (adminCompanyId != null) { // Nur prüfen, wenn der Admin einer Firma zugeordnet ist
                        User importingAdmin = userRepository.findAll().stream() // Ineffizient, besser Admin direkt übergeben oder laden
                                .filter(u -> u.getCompany() != null && u.getCompany().getId().equals(adminCompanyId) && u.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN")))
                                .findFirst().orElse(null);
                        boolean isSuperAdmin = importingAdmin != null && importingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

                        if (!isSuperAdmin && (user.getCompany() == null || !user.getCompany().getId().equals(adminCompanyId))) {
                            errors.add("Zeile " + rowNum + ": User '" + username + "' gehört nicht zur Firma des importierenden Admins oder Admin ist keiner Firma zugeordnet.");
                            continue;
                        }
                    }


                    LocalDateTime entryTimestamp = parseTimestampSafe(timestampStr, rowNum, username, errors);
                    if (entryTimestamp == null) {
                        continue;
                    }

                    if (punchTypeStr == null || punchTypeStr.trim().isEmpty()) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): PunchType fehlt.");
                        continue;
                    }
                    TimeTrackingEntry.PunchType punchType;
                    try {
                        punchType = TimeTrackingEntry.PunchType.valueOf(punchTypeStr.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiger PunchType '" + punchTypeStr + "'. Erwartet: START oder ENDE.");
                        continue;
                    }

                    TimeTrackingEntry.PunchSource source = TimeTrackingEntry.PunchSource.MANUAL_IMPORT;
                    if (sourceStr != null && !sourceStr.trim().isEmpty()) {
                        try {
                            source = TimeTrackingEntry.PunchSource.valueOf(sourceStr.trim().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültige Source '" + sourceStr + "'. Wird auf MANUAL_IMPORT gesetzt.");
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
                    errors.add("Zeile " + rowNum + ": Unerwarteter Fehler - " + e.getMessage());
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
        return result;
    }
}