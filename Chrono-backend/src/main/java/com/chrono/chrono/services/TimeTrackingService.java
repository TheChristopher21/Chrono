package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder; // Keep for other potential uses if any
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
    private PasswordEncoder passwordEncoder; // Keep for other potential uses
    @Autowired
    private VacationRequestRepository vacationRequestRepository;

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

    // MODIFIED METHOD
    @Transactional
    public String updateDayTimeEntries(
            String targetUsername, String date,
            String workStartStr, String breakStartStr, String breakEndStr, String workEndStr,
            String adminUsername // adminPassword and userPassword are removed
    ) {
        User targetUser = loadUserByUsername(targetUsername);
        User performingAdmin = loadUserByUsername(adminUsername);

        // Permission check (example: admin and target user must be in the same company, or admin is SUPERADMIN)
        boolean isSuperAdmin = performingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin) {
            if (performingAdmin.getCompany() == null || targetUser.getCompany() == null ||
                    !performingAdmin.getCompany().getId().equals(targetUser.getCompany().getId())) {
                throw new SecurityException("Admin " + adminUsername + " ist nicht berechtigt, Benutzer " + targetUsername + " zu bearbeiten (andere Firma oder keine Firma zugewiesen).");
            }
        }
        // Password checks previously here are removed.

        LocalDate day = LocalDate.parse(date);
        TimeTracking row = getOrCreateRow(targetUser, day);

        row.setWorkStart(parseLocalTimeSafe(workStartStr));
        row.setBreakStart(parseLocalTimeSafe(breakStartStr));
        row.setBreakEnd(parseLocalTimeSafe(breakEndStr));
        row.setWorkEnd(parseLocalTimeSafe(workEndStr));
        row.setCorrected(true); // Mark as edited by admin

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
            String userPassword, // This password is for the target user if they are editing themselves
            String actingUsername // This is the username of who is performing an edit (could be admin or self)
            // adminPassword parameter is removed
    ) {
        TimeTracking tt = timeTrackingRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found with ID: " + id));
        User targetUser = tt.getUser();
        User performingUser = loadUserByUsername(actingUsername);


        boolean isAdminAction = performingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (isAdminAction) {
            // Admin is performing the action
            boolean isSuperAdmin = performingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
            if (!isSuperAdmin) {
                if (performingUser.getCompany() == null || targetUser.getCompany() == null ||
                        !performingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                    throw new SecurityException("Admin " + actingUsername + " ist nicht berechtigt, Benutzer " + targetUser.getUsername() + " zu bearbeiten.");
                }
            }
            // Admin password check is removed.
            // If userPassword was for target user's confirmation of admin edit, it's not typically done this way.
            // The responsibility lies with the admin.
        } else {
            // Self-correction by user (actingUsername is the targetUser's username)
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

    private int getAdjustedExpectedWorkMinutes(User user, LocalDate date, List<VacationRequest> approvedVacations) {
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            return 0;
        }
        int baseExpectedMinutes = workScheduleService.computeExpectedWorkMinutes(user, date);

        for (VacationRequest vacation : approvedVacations) {
            if (vacation.isApproved() && !date.isBefore(vacation.getStartDate()) && !date.isAfter(vacation.getEndDate())) {
                if (vacation.isHalfDay()) {
                    return baseExpectedMinutes / 2;
                } else {
                    return 0;
                }
            }
        }
        return baseExpectedMinutes;
    }

    public int computeDailyWorkDifference(User user, LocalDate date, List<VacationRequest> approvedVacations) {
        int workedMinutes = getWorkedMinutes(user, date);
        int adjustedExpectedMinutes = getAdjustedExpectedWorkMinutes(user, date, approvedVacations);
        return workedMinutes - adjustedExpectedMinutes;
    }

    public int computeDailyWorkDifference(User user, LocalDate date) {
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);
        return computeDailyWorkDifference(user, date, approvedVacations);
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

        int expectedWeeklyMinutesAdjustedForVacation = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, weekStart, approvedVacationsInThisWeek);

        logger.debug("User: {}, Woche ab: {}, Gearbeitet: {}min, Erwartet (bereinigt): {}min, Differenz: {}",
                user.getUsername(), weekStart, totalWorkedMinutesInWeek, expectedWeeklyMinutesAdjustedForVacation, totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjustedForVacation);

        return totalWorkedMinutesInWeek - expectedWeeklyMinutesAdjustedForVacation;
    }

    @Transactional
    public void rebuildUserBalance(User user) {
        List<TimeTracking> rows = timeTrackingRepo.findByUser(user);
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);

        if (user.getIsHourly() == null) {
            user.setIsHourly(false);
        }
        if (user.getIsPercentage() == null) {
            user.setIsPercentage(false);
        }
        if (user.getTrackingBalanceInMinutes() == null) {
            user.setTrackingBalanceInMinutes(0);
        }


        if (rows.isEmpty() && approvedVacations.stream().noneMatch(vr -> vr.isApproved() && vr.isUsesOvertime())) {
            user.setTrackingBalanceInMinutes(0);
            userRepository.save(user);
            logger.info("Saldo für {} auf 0 gesetzt (keine Zeiteinträge und keine relevanten genehmigten Überstundenurlaube).", user.getUsername());
            return;
        }

        LocalDate firstDayToConsiderInitialized = LocalDate.now(ZoneId.of("Europe/Zurich"));

        Optional<LocalDate> firstTrackingDayOpt = rows.stream()
                .map(TimeTracking::getDailyDate)
                .min(LocalDate::compareTo);
        Optional<LocalDate> firstVacationDayOpt = approvedVacations.stream()
                .filter(VacationRequest::isApproved)
                .map(VacationRequest::getStartDate)
                .min(LocalDate::compareTo);

        if (firstTrackingDayOpt.isPresent() && firstVacationDayOpt.isPresent()) {
            firstDayToConsiderInitialized = firstTrackingDayOpt.get().isBefore(firstVacationDayOpt.get()) ? firstTrackingDayOpt.get() : firstVacationDayOpt.get();
        } else if (firstTrackingDayOpt.isPresent()) {
            firstDayToConsiderInitialized = firstTrackingDayOpt.get();
        } else {
            if (firstVacationDayOpt.isPresent()) {
                firstDayToConsiderInitialized = firstVacationDayOpt.get();
            }
        }

        final LocalDate firstDay = firstDayToConsiderInitialized;


        LocalDate lastDay = LocalDate.now(ZoneId.of("Europe/Zurich"));
        int totalMinutes = 0;

        if (!firstDay.isAfter(lastDay) || !rows.isEmpty() ||
                (approvedVacations.stream().anyMatch(VacationRequest::isApproved))) {

            logger.info("Saldo-Neuberechnung für {}: Zeitraum {} bis {}", user.getUsername(), firstDay, lastDay);
            if (Boolean.TRUE.equals(user.getIsPercentage())) {
                LocalDate currentWeekStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!currentWeekStart.isAfter(lastDay)) {
                    totalMinutes += computeWeeklyWorkDifferenceForPercentageUser(user, currentWeekStart, lastDay, approvedVacations);
                    currentWeekStart = currentWeekStart.plusWeeks(1);
                }
            } else {
                for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
                    totalMinutes += computeDailyWorkDifference(user, d, approvedVacations);
                }
            }
            user.setTrackingBalanceInMinutes(totalMinutes);
            logger.info("Saldo für {} neu berechnet: {} Minuten.", user.getUsername(), totalMinutes);
        } else {
            logger.info("Kein relevanter Zeitraum für Saldo-Neuberechnung für {}, Saldo bleibt unverändert: {}", user.getUsername(), Optional.ofNullable(user.getTrackingBalanceInMinutes()).orElse(0));
        }
        userRepository.save(user);
    }


    @Transactional
    public void rebuildAllUserBalancesOnce() {
        logger.info("Starte Saldo-Neuberechnung für alle User...");
        for (User u : userRepository.findAll()) {
            try {
                rebuildUserBalance(u);
            } catch (Exception e) {
                logger.error("Fehler bei der Saldo-Neuberechnung für User {}: {}", u.getUsername(), e.getMessage(), e);
            }
        }
        logger.info("✅ Saldo-Neuberechnung für alle User abgeschlossen.");
    }

    public int getWeeklyBalance(User user, LocalDate monday) {
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);

        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            LocalDate sunday = monday.plusDays(6);
            return computeWeeklyWorkDifferenceForPercentageUser(user, monday, sunday, approvedVacations);
        } else {
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                sum += computeDailyWorkDifference(user, monday.plusDays(i), approvedVacations);
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
            if (Boolean.TRUE.equals(user.getIsHourly())) {
                logger.info("Überspringe stündlichen Mitarbeiter {} für AutoPunchOut.", user.getUsername());
                continue;
            }

            logger.info("Bearbeite offenen Eintrag für User: {}, Datum: {}", user.getUsername(), today);
            boolean changed = false;

            if (entry.getWorkStart() != null && entry.getWorkEnd() == null) {
                if (Boolean.TRUE.equals(user.getIsPercentage()) &&
                        entry.getBreakStart() != null &&
                        entry.getBreakEnd() == null) {

                    logger.info("Prozentualer User {} hat WorkStart ({}) und BreakStart ({}) aber kein BreakEnd/WorkEnd. " +
                                    "Setze WorkEnd auf BreakStart-Zeit ({}) und entferne Pausenzeiten für {}.",
                            user.getUsername(), entry.getWorkStart(), entry.getBreakStart(), entry.getBreakStart(), today);

                    entry.setWorkEnd(entry.getBreakStart());
                    entry.setBreakStart(null);
                    entry.setBreakEnd(null);
                    changed = true;
                } else if (entry.getBreakStart() != null && entry.getBreakEnd() == null) {
                    logger.info("User {} ist noch in der Pause. Setze BreakEnd ({}) und WorkEnd ({}) für {}.",
                            user.getUsername(), autoPunchOutTime, autoPunchOutTime, today);
                    entry.setBreakEnd(autoPunchOutTime);
                    entry.setWorkEnd(autoPunchOutTime);
                    changed = true;
                } else {
                    logger.info("Setze WorkEnd ({}) für User {} am {}. (WorkStart: {}, BreakStart: {}, BreakEnd: {})",
                            autoPunchOutTime, user.getUsername(), today, entry.getWorkStart(), entry.getBreakStart(), entry.getBreakEnd());
                    entry.setWorkEnd(autoPunchOutTime);
                    changed = true;
                }
            }

            if (changed) {
                entry.setCorrected(true);
                logger.info("Eintrag für User {} am {} automatisch vervollständigt. WorkStart: {}, BreakStart: {}, BreakEnd: {}, WorkEnd: {}",
                        user.getUsername(), today, entry.getWorkStart(), entry.getBreakStart(), entry.getBreakEnd(), entry.getWorkEnd());

                try {
                    this.rebuildUserBalance(user);
                    logger.info("Saldo für User {} nach AutoPunchOut neu berechnet.", user.getUsername());
                } catch (Exception e) {
                    logger.error("Fehler bei der Saldo-Neuberechnung für User {} nach AutoPunchOut: {}", user.getUsername(), e.getMessage(), e);
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

    // REMOVE THIS METHOD as it's no longer directly needed by updateDayTimeEntries in this form.
    // Or, if it's used elsewhere, keep it but ensure its callers are aware of the changes.
    // For this specific request (removing password checks for admin actions),
    // the direct password validation logic is what we're removing or bypassing.
    /*
    private void checkAdminAndUserPasswords(
            User targetUser, String adminUsername, String adminPassword, String userPassword
    ) {
        // ... original implementation ...
        // This method can be removed if no other part of the service uses it after these changes.
        // If kept for other uses, ensure it handles cases where adminPassword might be null/empty.
    }
    */


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
        return timeTrackingRepo.findByUserOrderByDailyDateDesc(user);
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
            logger.warn("Ungültiges Datumsformat empfangen: '{}'. Wird als null interpretiert. Erwartet: yyyy-MM-dd", dateStr, e);
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
                rowIterator.next(); // Skip header
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
                    // ... (restliche Validierungen für die Zeile)
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
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Ungültiges Datumsformat '" + dateStr + "'. Erwartet: yyyy-MM-DD");
                        continue;
                    }
                    // ... (weitere Parsing und Validierungen für Zeiten)

                    LocalTime workStart = parseLocalTimeSafe(workStartStr);
                    if (workStart == null && (workStartStr != null && !workStartStr.trim().isEmpty()) ) { // Nur Fehler wenn was drin stand aber nicht parsebar war
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
                    // Zusätzliche Logik-Validierungen
                    if (workStart != null && workEnd != null && workEnd.isBefore(workStart)) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Arbeitsende (" + workEndStr + ") ist vor Arbeitsbeginn (" + workStartStr + ").");
                        continue;
                    }
                    if (breakStart != null && breakEnd != null && breakEnd.isBefore(breakStart)) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenende (" + breakEndStr + ") ist vor Pausenbeginn (" + breakStartStr + ").");
                        continue;
                    }
                    // Validierung, dass Pausen innerhalb der Arbeitszeit liegen (nur wenn alle Zeiten vorhanden)
                    if (workStart != null && workEnd != null && breakStart != null && (breakStart.isBefore(workStart) || breakStart.isAfter(workEnd))) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenbeginn (" + breakStartStr + ") liegt außerhalb der Arbeitszeit (" + workStartStr + "-" + workEndStr + ").");
                        continue;
                    }
                    if (workStart != null && workEnd != null && breakEnd != null && (breakEnd.isAfter(workEnd) || breakEnd.isBefore(workStart))) {
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Pausenende (" + breakEndStr + ") liegt außerhalb der Arbeitszeit (" + workStartStr + "-" + workEndStr + ").");
                        continue;
                    }
                    if (workStart == null && workEnd != null) { // Ende ohne Anfang
                        errors.add("Zeile " + rowNum + " (User: " + username + "): Arbeitsende ohne Arbeitsbeginn angegeben.");
                        continue;
                    }
                    if (breakStart == null && breakEnd != null) { // Pausenende ohne Pausenanfang
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