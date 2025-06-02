package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.SickLeaveRepository; // Importiert
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);

    @Autowired
    private TimeTrackingRepository timeTrackingRepo;
    @Autowired
    private WorkScheduleService workScheduleService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private VacationRequestRepository vacationRequestRepository;

    @Autowired
    private SickLeaveRepository sickLeaveRepository; // Injiziert

    public static final int FULL_DAY_MINUTES = 8 * 60 + 30;
    public static final int FULL_WEEK_MINUTES = FULL_DAY_MINUTES * 5;

    private User loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    @Transactional
    public TimeTracking getOrCreateRow(User user, LocalDate date) {
        return timeTrackingRepo.findByUserAndDailyDate(user, date)
                .orElseGet(() -> {
                    TimeTracking tt = new TimeTracking();
                    tt.setUser(user);
                    tt.setDailyDate(date);
                    tt.setCorrected(false);
                    tt.setWorkStart(null);
                    tt.setBreakStart(null);
                    tt.setBreakEnd(null);
                    tt.setWorkEnd(null);
                    return timeTrackingRepo.save(tt);
                });
    }

    @Transactional
    public TimeTracking getOrCreateTodayRow(User user) {
        return getOrCreateRow(user, LocalDate.now(ZoneId.of("Europe/Zurich")));
    }

    @Transactional
    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = loadUserByUsername(username);
        TimeTracking row = getOrCreateTodayRow(user);
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Zurich")).truncatedTo(ChronoUnit.MINUTES);

        if (row.getWorkStart() == null) {
            row.setWorkStart(now);
        } else if (row.getBreakStart() == null && row.getWorkEnd() == null) {
            row.setBreakStart(now);
        } else if (row.getBreakEnd() == null && row.getWorkEnd() == null) {
            row.setBreakEnd(now);
        } else if (row.getWorkEnd() == null) {
            row.setWorkEnd(now);
        } else {
            logger.info("SmartPunch für {}: Alle Stempelungen für heute ({}) bereits vorhanden.", username, row.getDailyDate());
        }
        row.setCorrected(false);
        timeTrackingRepo.save(row);
        rebuildUserBalance(user);
        return convertToResponse(row);
    }

    @Transactional
    public TimeTrackingResponse handlePunch(String username, String punchType) {
        User user = loadUserByUsername(username);
        TimeTracking row = getOrCreateTodayRow(user);
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Zurich")).truncatedTo(ChronoUnit.MINUTES);
        boolean changed = false;

        switch (punchType) {
            case "WORK_START":
                if (row.getWorkStart() == null) {
                    row.setWorkStart(now);
                    changed = true;
                } else
                    logger.warn("Punch für {}: WORK_START am {} bereits um {} vorhanden.", username, row.getDailyDate(), row.getWorkStart());
                break;
            case "BREAK_START":
                if (row.getWorkStart() != null && row.getBreakStart() == null && row.getWorkEnd() == null) {
                    row.setBreakStart(now);
                    changed = true;
                } else
                    logger.warn("Punch für {}: BREAK_START am {} nicht möglich (Bedingungen nicht erfüllt).", username, row.getDailyDate());
                break;
            case "BREAK_END":
                if (row.getBreakStart() != null && row.getBreakEnd() == null && row.getWorkEnd() == null) {
                    row.setBreakEnd(now);
                    changed = true;
                } else
                    logger.warn("Punch für {}: BREAK_END am {} nicht möglich (Bedingungen nicht erfüllt).", username, row.getDailyDate());
                break;
            case "WORK_END":
                if (row.getWorkStart() != null && row.getWorkEnd() == null) {
                    row.setWorkEnd(now);
                    changed = true;
                } else
                    logger.warn("Punch für {}: WORK_END am {} nicht möglich (Bedingungen nicht erfüllt).", username, row.getDailyDate());
                break;
            default:
                logger.warn("Unbekannter Punch-Typ: {}", punchType);
                break;
        }
        if (changed) {
            row.setCorrected(false);
            timeTrackingRepo.save(row);
            rebuildUserBalance(user);
        }
        return convertToResponse(row);
    }

    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = loadUserByUsername(username);
        List<TimeTracking> list = timeTrackingRepo.findByUserOrderByDailyDateDesc(user);
        return list.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String dateStr, String note) {
        User user = loadUserByUsername(username);
        LocalDate date = LocalDate.parse(dateStr);
        TimeTracking row = getOrCreateRow(user, date);
        row.setDailyNote(note);
        timeTrackingRepo.save(row);
        return convertToResponse(row);
    }

    @Transactional
    public String updateDayTimeEntries(
            String targetUsername, String date,
            String workStartStr, String breakStartStr, String breakEndStr, String workEndStr,
            String adminUsername
    ) {
        User targetUser = loadUserByUsername(targetUsername);
        User performingAdmin = loadUserByUsername(adminUsername);

        boolean isSuperAdmin = performingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin) {
            if (performingAdmin.getCompany() == null || targetUser.getCompany() == null ||
                    !performingAdmin.getCompany().getId().equals(targetUser.getCompany().getId())) {
                throw new SecurityException("Admin " + adminUsername + " ist nicht berechtigt, Benutzer " + targetUsername + " zu bearbeiten (andere Firma oder keine Firma zugewiesen).");
            }
        }

        LocalDate day = LocalDate.parse(date);
        TimeTracking row = getOrCreateRow(targetUser, day);

        row.setWorkStart(parseLocalTimeSafe(workStartStr));
        row.setBreakStart(parseLocalTimeSafe(breakStartStr));
        row.setBreakEnd(parseLocalTimeSafe(breakEndStr));
        row.setWorkEnd(parseLocalTimeSafe(workEndStr));
        row.setCorrected(true);

        timeTrackingRepo.save(row);
        rebuildUserBalance(targetUser);
        logger.info("Zeiteinträge für Benutzer {} am {} durch Admin {} erfolgreich aktualisiert.", targetUsername, date, adminUsername);
        return "Zeiteinträge erfolgreich aktualisiert.";
    }


    private LocalTime parseLocalTimeSafe(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "00:00".equals(timeStr) || "00:00:00".equals(timeStr)) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            logger.warn("Ungültiges Zeitformat empfangen: '{}'. Wird als null interpretiert.", timeStr, e);
            return null;
        }
    }

    @Transactional
    public TimeTrackingResponse updateTimeTrackEntry(
            Long id, LocalDateTime newStart, LocalDateTime newEnd,
            String userPassword,
            String actingUsername
    ) {
        TimeTracking tt = timeTrackingRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found with ID: " + id));
        User targetUser = tt.getUser();
        User performingUser = loadUserByUsername(actingUsername);


        boolean isAdminAction = performingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (isAdminAction) {
            boolean isSuperAdmin = performingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
            if (!isSuperAdmin) {
                if (performingUser.getCompany() == null || targetUser.getCompany() == null ||
                        !performingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                    throw new SecurityException("Admin " + actingUsername + " ist nicht berechtigt, Benutzer " + targetUser.getUsername() + " zu bearbeiten.");
                }
            }
        } else {
            if (!targetUser.getUsername().equals(actingUsername)) {
                throw new SecurityException("Nicht autorisierter Bearbeitungsversuch.");
            }
            if (userPassword == null || userPassword.trim().isEmpty() || !passwordEncoder.matches(userPassword, targetUser.getPassword())) {
                throw new IllegalArgumentException("Ungültiges Benutzerpasswort für die Selbstkorrektur.");
            }
        }

        tt.setWorkStart(newStart.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        tt.setWorkEnd(newEnd.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        tt.setCorrected(true);

        timeTrackingRepo.save(tt);
        rebuildUserBalance(targetUser);
        return convertToResponse(tt);
    }

    /**
     * Ermittelt das erwartete Arbeitssoll für einen Benutzer an einem bestimmten Datum unter Berücksichtigung
     * von Stundenlohn, Feiertagen, Urlaub und Krankheit.
     * Die Liste der `approvedVacations` wird übergeben, um wiederholte DB-Abfragen zu vermeiden,
     * aber die Kernlogik für das Soll (inkl. Urlaub, Krankheit, Feiertag) liegt im WorkScheduleService.
     */


    public int getAdjustedExpectedWorkMinutes(User user, LocalDate date, List<VacationRequest> approvedVacations) {
        // Die 'approvedVacations'-Liste wird bereits als Parameter empfangen.
        // Wir geben sie direkt an den WorkScheduleService weiter.
        // Stelle sicher, dass die Signatur von computeExpectedWorkMinutes in WorkScheduleService hier übereinstimmt.
        return workScheduleService.computeExpectedWorkMinutes(user, date, approvedVacations);
    }
    public int computeDailyWorkDifference(User user, LocalDate date, List<VacationRequest> approvedVacations) {
        int workedMinutes = getWorkedMinutes(user, date);
        // `approvedVacations` wird an `getAdjustedExpectedWorkMinutes` übergeben, aber die Kernlogik
        // zur Soll-Ermittlung (inkl. aller Abwesenheiten) liegt in `workScheduleService.computeExpectedWorkMinutes`.
        int adjustedExpectedMinutes = getAdjustedExpectedWorkMinutes(user, date, approvedVacations);
        return workedMinutes - adjustedExpectedMinutes;
    }

    public int computeDailyWorkDifference(User user, LocalDate date) {
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);
        // Die `approvedVacations` werden an die überladene Methode weitergegeben.
        return computeDailyWorkDifference(freshUser, date, approvedVacations);
    }

    private int computeWeeklyWorkDifferenceForPercentageUser(User user, LocalDate weekStart, LocalDate lastDayOverall, List<VacationRequest> allApprovedVacationsForUser) {
        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            return 0;
        }

        int totalWorkedMinutesInWeek = 0;
        LocalDate endOfWeek = weekStart.plusDays(6);

        for (LocalDate d = weekStart; !d.isAfter(endOfWeek) && !d.isAfter(lastDayOverall); d = d.plusDays(1)) {
            totalWorkedMinutesInWeek += getWorkedMinutes(user, d);
        }

        List<VacationRequest> approvedVacationsInThisWeek = allApprovedVacationsForUser.stream()
                .filter(vr -> vr.isApproved() &&
                        !vr.getEndDate().isBefore(weekStart) &&
                        !vr.getStartDate().isAfter(endOfWeek))
                .collect(Collectors.toList());

        // getExpectedWeeklyMinutesForPercentageUser im WorkScheduleService berücksichtigt Urlaub UND Krankheit
        int expectedWeeklyMinutesAdjusted = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, weekStart, approvedVacationsInThisWeek);

        logger.debug("TimeTrackingService.computeWeeklyWorkDifferenceForPercentageUser: User: {}, Woche ab: {}, Gearbeitet: {}min, Erwartet (bereinigt): {}min, Differenz für diese Woche: {}min",
                user.getUsername(), weekStart, totalWorkedMinutesInWeek, expectedWeeklyMinutesAdjusted, totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjusted);

        return totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjusted;
    }

    @Transactional
    public void rebuildUserBalance(User user) {
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + user.getId()));

        // Detailliertes Logging für den Start der Methode, spezifisch für Chantale oder allgemein
        // Dieses Logging wurde in der vorherigen Version schon hinzugefügt und ist hier beibehalten.
        if ("Chantale".equals(freshUser.getUsername())) {
            logger.info("[User: Chantale] Backend rebuildUserBalance: STARTE Saldo-Neuberechnung. DB-Konfig: isPercentage={}, expectedWorkDays={}, workPercentage={}, dailyWorkHours={}, scheduleCycle={}, scheduleEffectiveDate={}, weeklySchedule={}",
                    freshUser.getIsPercentage(), freshUser.getExpectedWorkDays(), freshUser.getWorkPercentage(), freshUser.getDailyWorkHours(), freshUser.getScheduleCycle(), freshUser.getScheduleEffectiveDate(), freshUser.getWeeklySchedule());
        } else {
            logger.debug("TimeTrackingService.rebuildUserBalance: Entering rebuildUserBalance for user: '{}'.", freshUser.getUsername());
        }

        List<TimeTracking> rows = timeTrackingRepo.findByUser(freshUser);
        // Wichtig: Lade die genehmigten Urlaube EINMAL für den User
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);

        // Logging der geladenen Urlaube für den spezifischen User
        if ("Chantale".equals(freshUser.getUsername())) { // Oder ein allgemeineres Log-Level/Bedingung
            logger.info("[User: {}] Geladene genehmigte Urlaube für Neuberechnung: Anzahl = {}, Details = {}",
                    freshUser.getUsername(),
                    approvedVacations.size(),
                    approvedVacations.stream()
                            .map(vr -> String.format("ID: %d, Start: %s, End: %s, HalfDay: %b, UsesOvertime: %b",
                                    vr.getId(), vr.getStartDate(), vr.getEndDate(), vr.isHalfDay(), vr.isUsesOvertime()))
                            .collect(Collectors.toList()));
        }
        // Krankmeldungen werden vom WorkScheduleService intern geladen, wenn `computeExpectedWorkMinutes` aufgerufen wird.

        if (freshUser.getIsHourly() == null) freshUser.setIsHourly(false);
        if (freshUser.getIsPercentage() == null) freshUser.setIsPercentage(false);
        if (freshUser.getTrackingBalanceInMinutes() == null) freshUser.setTrackingBalanceInMinutes(0);

        // Überprüfung, ob überhaupt Einträge oder Abwesenheiten vorhanden sind
        if (rows.isEmpty() && approvedVacations.isEmpty() && sickLeaveRepository.findByUser(freshUser).isEmpty()) {
            freshUser.setTrackingBalanceInMinutes(0);
            userRepository.save(freshUser);
            logger.info("Saldo für {} auf 0 gesetzt (keine Zeiteinträge, keine genehmigten Urlaube oder Krankmeldungen).", freshUser.getUsername());
            return;
        }

        // Ermittle den ersten relevanten Tag für die Berechnung
        LocalDate firstDayToConsider;
        Optional<LocalDate> firstTrackingDayOpt = rows.stream()
                .map(TimeTracking::getDailyDate)
                .min(LocalDate::compareTo);
        Optional<LocalDate> firstVacationDayOpt = approvedVacations.stream() // Nutze die bereits geladene Liste
                .map(VacationRequest::getStartDate)
                .min(LocalDate::compareTo);
        Optional<LocalDate> firstSickLeaveDayOpt = sickLeaveRepository.findByUser(freshUser).stream()
                .map(com.chrono.chrono.entities.SickLeave::getStartDate) // Stelle sicher, dass der Import korrekt ist
                .min(LocalDate::compareTo);

        List<Optional<LocalDate>> dateOpts = Arrays.asList(firstTrackingDayOpt, firstVacationDayOpt, firstSickLeaveDayOpt);
        firstDayToConsider = dateOpts.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now(ZoneId.of("Europe/Zurich"))); // Fallback auf heute, wenn keine Einträge/Abwesenheiten

        final LocalDate firstDay = firstDayToConsider;
        LocalDate lastDay = LocalDate.now(ZoneId.of("Europe/Zurich")); // Oder ein anderes sinnvolles Enddatum, z.B. das Datum des letzten Eintrags

        // Wenn das letzte Stempeldatum nach heute liegt (z.B. Zukunftseinträge), das letzte Datum anpassen
        Optional<LocalDate> lastTrackingDayOpt = rows.stream()
                .map(TimeTracking::getDailyDate)
                .max(LocalDate::compareTo);
        if(lastTrackingDayOpt.isPresent() && lastTrackingDayOpt.get().isAfter(lastDay)){
            lastDay = lastTrackingDayOpt.get();
        }


        int totalMinutes = 0;

        if (!firstDay.isAfter(lastDay)) { // Nur berechnen, wenn der Zeitraum gültig ist
            if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                logger.info("[User: {}] Backend rebuildUserBalance: Neuberechnung für Zeitraum {} bis {}", freshUser.getUsername(), firstDay, lastDay);
            } else {
                logger.info("Saldo-Neuberechnung für {}: Zeitraum {} bis {}", freshUser.getUsername(), firstDay, lastDay);
            }

            if (Boolean.TRUE.equals(freshUser.getIsPercentage())) {
                if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                    logger.info("[User: {}] Backend rebuildUserBalance: User ist prozentual. Berechne wöchentliche Differenzen.", freshUser.getUsername());
                } else {
                    logger.debug("TimeTrackingService.rebuildUserBalance: User '{}' is percentage based. Calculating weekly differences.", freshUser.getUsername());
                }
                LocalDate currentWeekStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!currentWeekStart.isAfter(lastDay)) {
                    // computeWeeklyWorkDifferenceForPercentageUser sollte `approvedVacations` verwenden,
                    // um die korrekte Liste der Urlaube für die jeweilige Woche zu filtern.
                    int weeklyDiff = computeWeeklyWorkDifferenceForPercentageUser(freshUser, currentWeekStart, lastDay, approvedVacations);
                    totalMinutes += weeklyDiff;
                    if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                        logger.info("[User: {}] Backend rebuildUserBalance (Percentage): Woche ab {}, Wöchentliche Diff: {}min, Kumuliert: {}min", freshUser.getUsername(), currentWeekStart, weeklyDiff, totalMinutes);
                    }
                    currentWeekStart = currentWeekStart.plusWeeks(1);
                }
            } else { // Standard-User oder stündlicher User (stündliche User haben typischerweise 0 Soll)
                if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                    logger.info("[User: {}] Backend rebuildUserBalance: User ist Standard/Stündlich. Berechne tägliche Differenzen.", freshUser.getUsername());
                } else {
                    logger.debug("TimeTrackingService.rebuildUserBalance: User '{}' is NOT percentage based. Calculating daily differences.", freshUser.getUsername());
                }
                for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
                    // Hier wird die Liste 'approvedVacations' an computeDailyWorkDifference übergeben
                    int dailyDifference = computeDailyWorkDifference(freshUser, d, approvedVacations);
                    totalMinutes += dailyDifference;

                    // Detailliertes Logging für den kritischen User (oder generell)
                    if ("Chantale".equals(freshUser.getUsername()) || logger.isTraceEnabled()) { // Log für Chantale immer, oder für alle bei TRACE Level
                        int worked = getWorkedMinutes(freshUser, d);
                        // getAdjustedExpectedWorkMinutes ruft intern workScheduleService.computeExpectedWorkMinutes auf
                        int expected = getAdjustedExpectedWorkMinutes(freshUser, d, approvedVacations); // Ruft die korrigierte Methode auf
                        logger.info("[User: {}] Backend rebuildUserBalance (Standard/Hourly): Datum {}, Ist: {}min, Soll: {}min (via computeExpectedWorkMinutes), Tagesdiff: {}min, Kumuliert: {}min",
                                freshUser.getUsername(), d, worked, expected, dailyDifference, totalMinutes);
                    }
                }
            }
            freshUser.setTrackingBalanceInMinutes(totalMinutes);
            if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                logger.info("[User: {}] Backend rebuildUserBalance: Saldo neu berechnet und wird gespeichert: {} Minuten.", freshUser.getUsername(), totalMinutes);
            } else {
                logger.info("Saldo für {} neu berechnet und wird gespeichert: {} Minuten.", freshUser.getUsername(), totalMinutes);
            }

        } else { // firstDay ist nach lastDay, oder es gab keine Einträge/Urlaube/Krankheit vor heute
            if ("Chantale".equals(freshUser.getUsername())) { // Oder allgemeines Logging
                logger.info("[User: {}] Backend rebuildUserBalance: Kein relevanter Zeitraum ({} bis {}) für Neuberechnung, Saldo bleibt unverändert bei: {}", freshUser.getUsername(), firstDay, lastDay, Optional.ofNullable(freshUser.getTrackingBalanceInMinutes()).orElse(0));
            } else {
                logger.info("Kein relevanter Zeitraum ({} bis {}) für Saldo-Neuberechnung für {}, Saldo bleibt unverändert bei: {}", firstDay, lastDay, freshUser.getUsername(), Optional.ofNullable(freshUser.getTrackingBalanceInMinutes()).orElse(0));
            }
            // Auch wenn kein Zeitraum, den aktuellen (wahrscheinlich 0 oder alten) Saldo speichern, falls Initialisierungen oben erfolgt sind.
            // Aber wenn der Saldo 0 war und keine Einträge, wurde oben schon return gemacht.
            // Hier geht es darum, dass wenn z.B. nur Zukunftsurlaub da ist, firstDay > lastDay sein könnte.
        }
        userRepository.save(freshUser); // Speichern des (möglicherweise aktualisierten) Users mit neuem Saldo
    }
    @Transactional
    public void rebuildAllUserBalancesOnce() {
        logger.info("Starte Saldo-Neuberechnung für alle User...");
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            try {
                rebuildUserBalance(u);
            } catch (Exception e) {
                logger.error("Fehler bei der Saldo-Neuberechnung für User {}: {}", u.getUsername(), e.getMessage(), e);
            }
        }
        logger.info("✅ Saldo-Neuberechnung für alle User abgeschlossen.");
    }

    public int getWeeklyBalance(User user, LocalDate monday) {
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(freshUser);

        if (Boolean.TRUE.equals(freshUser.getIsPercentage())) {
            LocalDate sunday = monday.plusDays(6);
            return computeWeeklyWorkDifferenceForPercentageUser(freshUser, monday, sunday, approvedVacations);
        } else {
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                sum += computeDailyWorkDifference(freshUser, monday.plusDays(i), approvedVacations);
            }
            return sum;
        }
    }

    public int getWorkedMinutes(User user, LocalDate date) {
        Optional<TimeTracking> ott = timeTrackingRepo.findByUserAndDailyDate(user, date);

        if (ott.isPresent()) {
            TimeTracking tt = ott.get();
            LocalTime workStart = tt.getWorkStart();
            LocalTime workEnd = tt.getWorkEnd();
            LocalTime breakStart = tt.getBreakStart();
            LocalTime breakEnd = tt.getBreakEnd();

            if (workStart == null || workEnd == null) return 0;
            if (workEnd.isBefore(workStart)) {
                logger.warn("User {} an Datum {}: Arbeitsende ({}) ist vor Arbeitsbeginn ({}). Arbeitszeit für diesen Tag wird als 0 gewertet.", user.getUsername(), date, workEnd, workStart);
                return 0;
            }

            long workDurationMinutes = ChronoUnit.MINUTES.between(workStart, workEnd);
            long breakDurationMinutes = 0;

            if (breakStart != null && breakEnd != null) {
                if (breakEnd.isBefore(breakStart)) {
                    logger.warn("User {} an Datum {}: Pausenende ({}) ist vor Pausenbeginn ({}). Pause wird als 0 gewertet.", user.getUsername(), date, breakEnd, breakStart);
                } else if (breakStart.isAfter(workEnd) || breakEnd.isBefore(workStart) || breakStart.isBefore(workStart) || breakEnd.isAfter(workEnd)) {
                    logger.warn("User {} an Datum {}: Pause ({}-{}) liegt teilweise oder ganz außerhalb der Arbeitszeit ({}-{}). Pause wird als 0 gewertet.", user.getUsername(), date, breakStart, breakEnd, workStart, workEnd);
                } else {
                    breakDurationMinutes = ChronoUnit.MINUTES.between(breakStart, breakEnd);
                }
            }

            return (int) Math.max(0, workDurationMinutes - breakDurationMinutes);
        }
        return 0;
    }

    @Scheduled(cron = "0 20 23 * * MON-FRI", zone = "Europe/Zurich")
    @Transactional
    public void autoPunchOutForgottenEntries() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Zurich"));
        LocalTime autoPunchOutTime = LocalTime.of(23, 20);

        logger.info("AutoPunchOutForgottenEntries gestartet für Datum: {} um {}", today, autoPunchOutTime);

        List<TimeTracking> openEntries = timeTrackingRepo.findByDailyDateAndWorkStartIsNotNullAndWorkEndIsNull(today);

        if (openEntries.isEmpty()) {
            logger.info("Keine offenen Einträge für AutoPunchOut am {} gefunden.", today);
            return;
        }

        for (TimeTracking entry : openEntries) {
            User user = entry.getUser();
            User freshUser = userRepository.findById(user.getId()).orElse(null);
            if (freshUser == null) {
                logger.error("User mit ID {} für AutoPunchOut nicht gefunden, überspringe Eintrag ID {}.", user.getId(), entry.getId());
                continue;
            }

            if (Boolean.TRUE.equals(freshUser.getIsHourly())) {
                logger.info("Überspringe stündlichen Mitarbeiter {} für AutoPunchOut.", freshUser.getUsername());
                continue;
            }

            logger.info("Bearbeite offenen Eintrag für User: {}, Datum: {}", freshUser.getUsername(), today);
            boolean changed = false;

            if (entry.getWorkStart() != null && entry.getWorkEnd() == null) {
                if (Boolean.TRUE.equals(freshUser.getIsPercentage()) &&
                        entry.getBreakStart() != null &&
                        entry.getBreakEnd() == null) {

                    logger.info("Prozentualer User {} hat WorkStart ({}) und BreakStart ({}) aber kein BreakEnd/WorkEnd. " +
                                    "Setze WorkEnd auf BreakStart-Zeit ({}) und entferne Pausenzeiten für {}.",
                            freshUser.getUsername(), entry.getWorkStart(), entry.getBreakStart(), entry.getBreakStart(), today);

                    entry.setWorkEnd(entry.getBreakStart());
                    entry.setBreakStart(null);
                    entry.setBreakEnd(null);
                    changed = true;
                } else if (entry.getBreakStart() != null && entry.getBreakEnd() == null) {
                    logger.info("User {} ist noch in der Pause. Setze BreakEnd ({}) und WorkEnd ({}) für {}.",
                            freshUser.getUsername(), autoPunchOutTime, autoPunchOutTime, today);
                    entry.setBreakEnd(autoPunchOutTime);
                    entry.setWorkEnd(autoPunchOutTime);
                    changed = true;
                } else {
                    logger.info("Setze WorkEnd ({}) für User {} am {}. (WorkStart: {}, BreakStart: {}, BreakEnd: {})",
                            autoPunchOutTime, freshUser.getUsername(), today, entry.getWorkStart(), entry.getBreakStart(), entry.getBreakEnd());
                    entry.setWorkEnd(autoPunchOutTime);
                    changed = true;
                }
            }

            if (changed) {
                entry.setCorrected(true);
                logger.info("Eintrag für User {} am {} automatisch vervollständigt. WorkStart: {}, BreakStart: {}, BreakEnd: {}, WorkEnd: {}",
                        freshUser.getUsername(), today, entry.getWorkStart(), entry.getBreakStart(), entry.getBreakEnd(), entry.getWorkEnd());

                try {
                    this.rebuildUserBalance(freshUser);
                    logger.info("Saldo für User {} nach AutoPunchOut neu berechnet.", freshUser.getUsername());
                } catch (Exception e) {
                    logger.error("Fehler bei der Saldo-Neuberechnung für User {} nach AutoPunchOut: {}", freshUser.getUsername(), e.getMessage(), e);
                }
            }
        }
        logger.info("✅ AutoPunchOutForgottenEntries beendet für Datum: {}", today);
    }

    public List<TimeReportDTO> getReport(String username, String startDate, String endDate) {
        User user = loadUserByUsername(username);
        LocalDate from = LocalDate.parse(startDate);
        LocalDate to = LocalDate.parse(endDate);

        List<TimeTracking> entries = timeTrackingRepo
                .findByUserAndDailyDateBetweenOrderByDailyDateAsc(user, from, to);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<TimeReportDTO> report = new ArrayList<>();

        for (TimeTracking row : entries) {
            String dayStr = row.getDailyDate().format(dateFormatter);

            String ws = (row.getWorkStart() != null) ? row.getWorkStart().format(timeFormatter) : "-";
            String bs = (row.getBreakStart() != null) ? row.getBreakStart().format(timeFormatter) : "-";
            String be = (row.getBreakEnd() != null) ? row.getBreakEnd().format(timeFormatter) : "-";
            String we = (row.getWorkEnd() != null) ? row.getWorkEnd().format(timeFormatter) : "-";

            report.add(new TimeReportDTO(
                    user.getUsername(), dayStr, ws, bs, be, we, row.getDailyNote()
            ));
        }
        return report;
    }

    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        return new TimeTrackingResponse(
                tt.getId(),
                tt.getDailyDate(),
                tt.getWorkStart(),
                tt.getBreakStart(),
                tt.getBreakEnd(),
                tt.getWorkEnd(),
                tt.getDailyNote(),
                tt.isCorrected()
        );
    }

    public List<TimeTracking> getTimeTrackingRowsForUser(User user) {
        User freshUser = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        return timeTrackingRepo.findByUserOrderByDailyDateDesc(freshUser);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new DataFormatter().formatCellValue(cell);
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return new DataFormatter().formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator());
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    private LocalDate parseLocalDateSafe(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            logger.warn("Ungültiges Datumsformat empfangen: '{}'. Wird als null interpretiert. Erwartet:<y_bin_46>-MM-dd", dateStr, e);
            return null;
        }
    }

    @Transactional
    public Map<String, Object> importTimeTrackingFromExcel(InputStream inputStream, Long adminCompanyId) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        int importedCount = 0;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
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
                    String dateStr = getCellValueAsString(row.getCell(1));
                    String workStartStr = getCellValueAsString(row.getCell(2));
                    String breakStartStr = getCellValueAsString(row.getCell(3));
                    String breakEndStr = getCellValueAsString(row.getCell(4));
                    String workEndStr = getCellValueAsString(row.getCell(5));
                    String dailyNote = getCellValueAsString(row.getCell(6));

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
                    if (user.getCompany() == null || !user.getCompany().getId().equals(adminCompanyId)) {
                        errors.add("Zeile " + rowNum + ": User '" + username + "' gehört nicht zur Firma des Admins.");
                        continue;
                    }

                    LocalDate date = parseLocalDateSafe(dateStr);
                    if (date == null) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Datumsformat '" + dateStr + "'. Erwartet:<y_bin_46>-MM-DD");
                        continue;
                    }

                    LocalTime workStart = parseLocalTimeSafe(workStartStr);
                    if (workStart == null && (workStartStr != null && !workStartStr.trim().isEmpty()) ) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Zeitformat für Arbeitsbeginn '" + workStartStr + "'. Erwartet: HH:mm");
                        continue;
                    }
                    LocalTime workEnd = parseLocalTimeSafe(workEndStr);
                    if (workEnd == null && (workEndStr != null && !workEndStr.trim().isEmpty()) ) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Zeitformat für Arbeitsende '" + workEndStr + "'. Erwartet: HH:mm");
                        continue;
                    }
                    LocalTime breakStart = parseLocalTimeSafe(breakStartStr);
                    if (breakStart == null && (breakStartStr != null && !breakStartStr.trim().isEmpty()) ) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Zeitformat für Pausenbeginn '" + breakStartStr + "'. Erwartet: HH:mm");
                        continue;
                    }
                    LocalTime breakEnd = parseLocalTimeSafe(breakEndStr);
                    if (breakEnd == null && (breakEndStr != null && !breakEndStr.trim().isEmpty()) ) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Zeitformat für Pausenende '" + breakEndStr + "'. Erwartet: HH:mm");
                        continue;
                    }
                    if (workStart != null && workEnd != null && workEnd.isBefore(workStart)) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Arbeitsende (" + workEndStr + ") ist vor Arbeitsbeginn (" + workStartStr + ").");
                        continue;
                    }
                    if (breakStart != null && breakEnd != null && breakEnd.isBefore(breakStart)) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenende (" + breakEndStr + ") ist vor Pausenbeginn (" + breakStartStr + ").");
                        continue;
                    }
                    if (workStart != null && workEnd != null && breakStart != null && (breakStart.isBefore(workStart) || breakStart.isAfter(workEnd))) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenbeginn (" + breakStartStr + ") liegt außerhalb der Arbeitszeit (" + workStartStr + "-" + workEndStr + ").");
                        continue;
                    }
                    if (workStart != null && workEnd != null && breakEnd != null && (breakEnd.isAfter(workEnd) || breakEnd.isBefore(workStart))) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenende (" + breakEndStr + ") liegt außerhalb der Arbeitszeit (" + workStartStr + "-" + workEndStr + ").");
                        continue;
                    }
                    if (workStart == null && workEnd != null) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Arbeitsende ohne Arbeitsbeginn angegeben.");
                        continue;
                    }
                    if (breakStart == null && breakEnd != null) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenende ohne Pausenbeginn angegeben.");
                        continue;
                    }


                    TimeTracking timeTracking = getOrCreateRow(user, date);
                    timeTracking.setWorkStart(workStart);
                    timeTracking.setBreakStart(breakStart);
                    timeTracking.setBreakEnd(breakEnd);
                    timeTracking.setWorkEnd(workEnd);
                    timeTracking.setDailyNote(dailyNote);
                    timeTracking.setCorrected(true);

                    timeTrackingRepo.save(timeTracking);
                    rebuildUserBalance(user);
                    successes.add("Zeile " + rowNum + ": Eintrag für User '" + username + "' am " + dateStr + " erfolgreich importiert/aktualisiert.");
                    importedCount++;

                } catch (Exception e) {
                    logger.error("Fehler beim Verarbeiten der Zeile {}: {}", rowNum, e.getMessage(), e);
                    errors.add("Zeile " + rowNum + ": Unerwarteter Fehler - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Fehler beim Import der Excel-Datei: {}", e.getMessage(), e);
            errors.add("Genereller Fehler beim Lesen der Excel-Datei: " + e.getMessage());
        }

        result.put("importedCount", importedCount);
        result.put("successMessages", successes);
        result.put("errorMessages", errors);
        return result;
    }
}