package com.chrono.chrono.services;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;

    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    public TimeTrackingResponse handlePunch(String username, String newStatus) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        Optional<TimeTracking> activePunchOpt =
                timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        TimeTracking activePunch = activePunchOpt.orElse(null);

        TimeTracking lastEntry =
                timeTrackingRepository.findTopByUserOrderByIdDesc(user).orElse(null);

        Integer lastPunchOrder = (activePunch != null)
                ? activePunch.getPunchOrder()
                : (lastEntry != null ? lastEntry.getPunchOrder() : null);

        if (lastPunchOrder == null || lastPunchOrder == 0) {
            lastPunchOrder = 4;
        }
        logger.info("handlePunch: Für User '{}' wurde letzter PunchOrder = {} festgestellt.", username, lastPunchOrder);
        logger.info("handlePunch: Angeforderter neuer Status = {}", newStatus);

        if (!isTransitionAllowed(lastPunchOrder, newStatus)) {
            String errorMsg = "Transition not allowed from punchOrder = " + lastPunchOrder + " to " + newStatus;
            logger.error("handlePunch: " + errorMsg);
            throw new RuntimeException(errorMsg);
        }

        if (activePunch != null) {
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
            logger.info("handlePunch: Aktiver Punch für User '{}' erfolgreich abgeschlossen.", username);
        }

        int nextOrder = mapStatusToPunchOrder(newStatus);
        TimeTracking newEntry = createNewPunch(user, nextOrder);
        logger.info("handlePunch: Neuer Punch mit Order {} für User '{}' erstellt.", nextOrder, username);
        return convertToResponse(newEntry);
    }

    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        // Prüfe, ob bereits ein vollständiger Arbeitszyklus abgeschlossen wurde (PunchOrder 4)
        Optional<TimeTracking> lastCompleteTodayOpt = timeTrackingRepository
                .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(
                        user, startOfDay, endOfDay
                );
        if (lastCompleteTodayOpt.isPresent() && lastCompleteTodayOpt.get().getPunchOrder() == 4) {
            throw new RuntimeException("Für heute wurde bereits ein kompletter Arbeitszyklus abgeschlossen.");
        }

        // Falls noch ein offener Punch vorhanden ist, beenden wir ihn
        Optional<TimeTracking> activePunchOpt =
                timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        if (activePunchOpt.isPresent()) {
            TimeTracking activePunch = activePunchOpt.get();
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
            logger.info("handleSmartPunch: Aktiver Punch für User '{}' abgeschlossen.", username);
        }

        // Ermittele nun den höchsten PunchOrder des Tages (unabhängig, ob offen oder abgeschlossen)
        List<TimeTracking> todaysEntries = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startOfDay, endOfDay);

        // Bestimme den maximalen (letzten) PunchOrder für heute
        Integer lastPunchOrder = todaysEntries.stream()
                .map(tt -> tt.getPunchOrder() != null ? tt.getPunchOrder() : 0)
                .max(Integer::compare)
                .orElse(4);

        // Falls kein Eintrag vorliegt, setze den Standard (4 als Ausgangspunkt, sodass der erste Stempel WORK_START erfolgt)
        if (lastPunchOrder == 0) {
            lastPunchOrder = 4;
        }

        logger.info("handleSmartPunch: Für User '{}' wurde letzter PunchOrder = {} ermittelt.", username, lastPunchOrder);

        // Bestimme den nächsten Status anhand des höchsten PunchOrder:
        String nextStatus;
        if (lastPunchOrder == 4) {
            nextStatus = "WORK_START";
        } else if (lastPunchOrder == 1) {
            nextStatus = "BREAK_START";
        } else if (lastPunchOrder == 2) {
            nextStatus = "BREAK_END";
        } else if (lastPunchOrder == 3) {
            nextStatus = "WORK_END";
        } else {
            nextStatus = "WORK_START";
        }
        logger.info("handleSmartPunch: Nächster erlaubter Status für User '{}' ist {}", username, nextStatus);
        return handlePunch(username, nextStatus);
    }

    /**
     * AutoPunchOut – Diese Methode läuft täglich um 23:00 Uhr und schließt alle offenen
     * Zeiteinträge des aktuellen Tages automatisch ab.
     */
    @Scheduled(cron = "0 */5 * * * *") // Führt autoPunchOut jede volle Minute aus (zum Testen)
    @Transactional
    public void autoPunchOut() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime autoEndTime = today.atTime(23, 59, 59); // Kannst Du auch für den Test anpassen

        logger.info("AutoPunchOut gestartet für den Tag {}. StartOfDay: {}, AutoEndTime: {}",
                today, startOfDay, autoEndTime);

        try {
            // Hole alle offenen Einträge (ohne EndTime) von heute
            List<TimeTracking> openPunches = timeTrackingRepository
                    .findByEndTimeIsNullAndStartTimeBetween(startOfDay, autoEndTime);
            logger.info("AutoPunchOut: Gefundene offene Einträge: {}", openPunches.size());

            // Gruppiere offene Einträge nach User
            Map<User, List<TimeTracking>> userMap = openPunches.stream()
                    .collect(Collectors.groupingBy(TimeTracking::getUser));

            for (Map.Entry<User, List<TimeTracking>> entry : userMap.entrySet()) {
                User user = entry.getKey();
                logger.info("AutoPunchOut: Verarbeite offene Einträge für User: {}", user.getUsername());
                try {
                    // Alle heutigen Einträge (offen und abgeschlossen) für diesen User
                    List<TimeTracking> entriesToday = timeTrackingRepository
                            .findByUserAndStartTimeBetween(user, startOfDay, autoEndTime);

                    if (entriesToday.isEmpty()) {
                        logger.info("AutoPunchOut: {} hat heute keine Einträge. Überspringe.", user.getUsername());
                        continue;
                    }

                    // Bestehende PunchOrders ermitteln
                    Set<Integer> orders = entriesToday.stream()
                            .map(tt -> tt.getPunchOrder() != null ? tt.getPunchOrder() : 0)
                            .collect(Collectors.toSet());
                    logger.info("AutoPunchOut: {} hat folgende PunchOrders: {}", user.getUsername(), orders);

                    if (orders.contains(1) && orders.contains(2) && orders.contains(3) && orders.contains(4)) {
                        logger.info("AutoPunchOut: {} hat bereits einen vollständigen Arbeitszyklus (1 bis 4).", user.getUsername());
                        continue;
                    }

                    for (int i = 1; i <= 4; i++) {
                        final int punch = i;
                        if (!orders.contains(punch)) {
                            TimeTracking newPunch = new TimeTracking();
                            newPunch.setUser(user);
                            newPunch.setPunchOrder(punch);
                            LocalDateTime punchTime = autoEndTime.plusSeconds(punch * 10);
                            newPunch.setStartTime(punchTime);
                            if (punch == 3 || punch == 4) {
                                newPunch.setEndTime(punchTime);
                            }
                            newPunch.setCorrected(true);
                            timeTrackingRepository.save(newPunch);
                            logger.info("AutoPunchOut: Für {} wurde fehlender Punch {} erstellt (StartTime: {}).",
                                    user.getUsername(), punch, punchTime);
                        } else {
                            Optional<TimeTracking> openEntryOpt = entriesToday.stream()
                                    .filter(tt -> tt.getPunchOrder() == punch && tt.getEndTime() == null)
                                    .findFirst();
                            if (openEntryOpt.isPresent()) {
                                TimeTracking openEntry = openEntryOpt.get();
                                LocalDateTime newEndTime = autoEndTime.plusSeconds(punch * 10);
                                openEntry.setEndTime(newEndTime);
                                openEntry.setCorrected(true);
                                timeTrackingRepository.save(openEntry);
                                logger.info("AutoPunchOut: Für {} wurde offener Punch {} aktualisiert (EndTime: {}).",
                                        user.getUsername(), punch, newEndTime);
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("AutoPunchOut: Fehler bei der Verarbeitung von User '{}': {}", user.getUsername(), ex.getMessage());
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            logger.error("AutoPunchOut: Allgemeiner Fehler: {}", ex.getMessage());
            ex.printStackTrace();
        }
        logger.info("AutoPunchOut abgeschlossen für den Tag {}.", today);
    }



    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String date, String note) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!user.isHourly()) {
            throw new RuntimeException("Daily note is only available for hourly employees.");
        }
        LocalDate localDate = LocalDate.parse(date);
        List<TimeTracking> noteEntries = timeTrackingRepository.findDailyNoteByUserAndDate(user, localDate);
        TimeTracking noteEntry;
        if (!noteEntries.isEmpty()) {
            noteEntry = noteEntries.get(0);
            noteEntry.setDailyNote(note);
        } else {
            noteEntry = new TimeTracking();
            noteEntry.setUser(user);
            noteEntry.setPunchOrder(0);
            noteEntry.setStartTime(localDate.atStartOfDay());
            noteEntry.setEndTime(localDate.atStartOfDay());
            noteEntry.setDailyNote(note);
            noteEntry.setCorrected(true);
        }
        noteEntry.setDailyDate(localDate);
        timeTrackingRepository.save(noteEntry);
        logger.info("updateDailyNote: Daily note für User '{}' am {} aktualisiert.", username, date);
        return convertToResponse(noteEntry);
    }

    @Transactional
    public String updateDayTimeEntries(
            String targetUsername,
            String date,
            String workStartStr,
            String breakStartStr,
            String breakEndStr,
            String workEndStr,
            String adminUsername,
            String adminPassword,
            String userPassword
    ) {
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            User adminUser = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            String storedAdminPwd = adminUser.getAdminPassword();
            if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
                storedAdminPwd = adminUser.getPassword();
                logger.info("updateDayTimeEntries: Kein separates Admin-Passwort vorhanden, verwende Login-Passwort.");
            }

            if (targetUsername.equals(adminUsername)) {
                logger.info("updateDayTimeEntries: Self-edit detected for user '{}'.", targetUsername);
                if (!adminPassword.equals(userPassword)) {
                    throw new RuntimeException("For self-edit, admin and user passwords must match");
                }
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                logger.info("updateDayTimeEntries: Self-edit password match: adminPwdMatch = {}, userPwdMatch = {}",
                        adminPwdMatch, userPwdMatch);
                if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
                if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
            } else {
                logger.info("updateDayTimeEntries: Editing entry of another user: '{}'.", targetUsername);
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                logger.info("updateDayTimeEntries: Other edit password match: adminPwdMatch = {}, userPwdMatch = {}",
                        adminPwdMatch, userPwdMatch);
                if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
                if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
            }
        }

        LocalDate parsedDate = LocalDate.parse(date);
        LocalTime newWorkStart = LocalTime.parse(workStartStr);
        LocalTime newWorkEnd   = LocalTime.parse(workEndStr);
        boolean hasBreak       = !(breakStartStr.equals("00:00") && breakEndStr.equals("00:00"));
        LocalTime newBreakStart = hasBreak ? LocalTime.parse(breakStartStr) : null;
        LocalTime newBreakEnd   = hasBreak ? LocalTime.parse(breakEndStr)   : null;

        LocalDateTime newWorkStartDT  = parsedDate.atTime(newWorkStart);
        LocalDateTime newWorkEndDT    = parsedDate.atTime(newWorkEnd);
        LocalDateTime newBreakStartDT = hasBreak ? parsedDate.atTime(newBreakStart) : null;
        LocalDateTime newBreakEndDT   = hasBreak ? parsedDate.atTime(newBreakEnd)   : null;

        LocalDateTime dayStart = parsedDate.atStartOfDay();
        LocalDateTime dayEnd   = parsedDate.plusDays(1).atStartOfDay();

        List<TimeTracking> oldEntries =
                timeTrackingRepository.findByUserAndStartTimeBetween(targetUser, dayStart, dayEnd);
        timeTrackingRepository.deleteAll(oldEntries);
        logger.info("updateDayTimeEntries: Deleted {} old entries for date {}.", oldEntries.size(), date);

        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);
        logger.info("updateDayTimeEntries: Created Work Start entry: {}", ts1);

        if (hasBreak) {
            TimeTracking ts2 = new TimeTracking();
            ts2.setUser(targetUser);
            ts2.setPunchOrder(2);
            ts2.setStartTime(newBreakStartDT);
            ts2.setBreakStart(newBreakStart);
            ts2.setCorrected(true);
            timeTrackingRepository.save(ts2);
            logger.info("updateDayTimeEntries: Created Break Start entry: {}", ts2);

            TimeTracking ts3 = new TimeTracking();
            ts3.setUser(targetUser);
            ts3.setPunchOrder(3);
            ts3.setStartTime(newBreakEndDT);
            ts3.setBreakEnd(newBreakEnd);
            ts3.setCorrected(true);
            timeTrackingRepository.save(ts3);
            logger.info("updateDayTimeEntries: Created Break End entry: {}", ts3);
        }

        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);
        logger.info("updateDayTimeEntries: Created Work End entry: {}", ts4);

        return "Day entries updated successfully (old entries removed, new entries created)";
    }

    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        List<TimeTracking> all = timeTrackingRepository.findAllWithUser();
        return all.stream()
                .map(tt -> new AdminTimeTrackDTO(
                        tt.getId(),
                        tt.getUser().getUsername(),
                        tt.getStartTime(),
                        tt.getEndTime(),
                        tt.isCorrected(),
                        tt.getPunchOrder() != null ? tt.getPunchOrder() : 0,
                        tt.getUser().getColor()
                ))
                .collect(Collectors.toList());
    }

    public AdminTimeTrackDTO updateTimeTrackEntry(
            Long id,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            String userPassword,
            String adminUsername,
            String adminPassword
    ) {
        logger.info("updateTimeTrackEntry: Aufruf mit id: {}", id);
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));
        User targetUser = tt.getUser();
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        String storedAdminPwd = adminUser.getAdminPassword();
        if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
            storedAdminPwd = adminUser.getPassword();
            logger.info("updateTimeTrackEntry: Kein separates Admin-Passwort gefunden; verwende Login-Passwort.");
        }

        if (adminUsername.equals(targetUser.getUsername())) {
            logger.info("updateTimeTrackEntry: Selbstbearbeitung erkannt für User '{}'.", targetUser.getUsername());
            if (!adminPassword.equals(userPassword)) {
                throw new RuntimeException("For self-edit, admin and user passwords must match");
            }
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch = passwordEncoder.matches(userPassword, targetUser.getPassword());
            logger.info("updateTimeTrackEntry: Self-edit Passwortprüfung: adminPwdMatch = {}, userPwdMatch = {}",
                    adminPwdMatch, userPwdMatch);
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch) throw new RuntimeException("Invalid user password");
        } else {
            logger.info("updateTimeTrackEntry: Bearbeite Eintrag eines anderen Benutzers: '{}'.", targetUser.getUsername());
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch = passwordEncoder.matches(userPassword, targetUser.getPassword());
            logger.info("updateTimeTrackEntry: Bearbeitungs-Passwortprüfung: adminPwdMatch = {}, userPwdMatch = {}",
                    adminPwdMatch, userPwdMatch);
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch) throw new RuntimeException("Invalid user password");
        }

        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);
        logger.info("updateTimeTrackEntry: Aktualisierter TimeTracking-Eintrag: {}", updated);

        return new AdminTimeTrackDTO(
                updated.getId(),
                updated.getUser().getUsername(),
                updated.getStartTime(),
                updated.getEndTime(),
                updated.isCorrected(),
                updated.getPunchOrder() != null ? updated.getPunchOrder() : 0,
                updated.getUser().getColor()
        );
    }

    private boolean isTransitionAllowed(Integer lastPunchOrder, String newStatus) {
        logger.info("Prüfe Zustandsübergang: Letzter PunchOrder = {}, angeforderter Status = {}", lastPunchOrder, newStatus);
        switch (newStatus) {
            case "WORK_START":
                boolean allowedWorkStart = (lastPunchOrder == null || lastPunchOrder == 4);
                logger.info("WORK_START transition allowed: {}", allowedWorkStart);
                return allowedWorkStart;
            case "BREAK_START":
                // BREAK_START darf nur folgen, wenn der letzte Punch 1 war.
                boolean allowedBreakStart = (lastPunchOrder != null && lastPunchOrder == 1);
                logger.info("BREAK_START transition allowed: {}", allowedBreakStart);
                return allowedBreakStart;
            case "BREAK_END":
                // BREAK_END darf nur folgen, wenn der letzte Punch 2 war.
                boolean allowedBreakEnd = (lastPunchOrder != null && lastPunchOrder == 2);
                logger.info("BREAK_END transition allowed: {}", allowedBreakEnd);
                return allowedBreakEnd;
            case "WORK_END":
                // WORK_END darf nur folgen, wenn der letzte Punch 1 oder 3 war.
                boolean allowedWorkEnd = (lastPunchOrder != null && (lastPunchOrder == 1 || lastPunchOrder == 3));
                logger.info("WORK_END transition allowed: {}", allowedWorkEnd);
                return allowedWorkEnd;
            default:
                logger.warn("Unbekannter angeforderter Status: {}", newStatus);
                return false;
        }
    }

    private int mapStatusToPunchOrder(String newStatus) {
        switch (newStatus) {
            case "WORK_START":
                return 1;
            case "BREAK_START":
                return 2;
            case "BREAK_END":
                return 3;
            case "WORK_END":
                return 4;
            default:
                return 0;
        }
    }

    private TimeTracking createNewPunch(User user, int punchOrder) {
        TimeTracking tt = new TimeTracking();
        tt.setUser(user);
        tt.setPunchOrder(punchOrder);
        LocalDateTime now = LocalDateTime.now();
        tt.setStartTime(now);
        if (punchOrder == 4) {
            tt.setEndTime(now);
        }
        tt.setCorrected(false);
        TimeTracking saved = timeTrackingRepository.save(tt);
        logger.info("createNewPunch: Neuer Punch mit Order {} für User '{}' erstellt um {}.", punchOrder, user.getUsername(), now);
        return saved;
    }

    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        Integer pOrder = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
        String label;
        switch (pOrder) {
            case 1:
                label = "Work Start";
                break;
            case 2:
                label = "Break Start";
                break;
            case 3:
                label = "Break End";
                break;
            case 4:
                label = "Work End";
                break;
            case 0:
                label = "Daily Note";
                break;
            default:
                label = "Unknown";
        }
        return new TimeTrackingResponse(
                tt.getId(),
                tt.getStartTime(),
                tt.getEndTime(),
                tt.isCorrected(),
                label,
                pOrder,
                tt.getDailyNote()
        );
    }

    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<TimeTracking> entries = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startDateTime, endDateTime);

        Map<LocalDate, List<TimeTracking>> grouped = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getStartTime().toLocalDate()));

        List<TimeReportDTO> report = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedDates);

        for (LocalDate date : sortedDates) {
            List<TimeTracking> dayEntries = grouped.get(date);
            dayEntries.sort(Comparator.comparingInt(
                    e -> e.getPunchOrder() != null ? e.getPunchOrder() : 0
            ));

            String workStart = "-";
            String breakStart = "-";
            String breakEnd = "-";
            String workEnd = "-";
            String dailyNote = "";

            for (TimeTracking e : dayEntries) {
                int order = e.getPunchOrder() != null ? e.getPunchOrder() : 0;
                if (order == 1 && workStart.equals("-")) {
                    workStart = e.getWorkStart() != null
                            ? e.getWorkStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 2 && breakStart.equals("-")) {
                    breakStart = e.getBreakStart() != null
                            ? e.getBreakStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 3 && breakEnd.equals("-")) {
                    breakEnd = e.getBreakEnd() != null
                            ? e.getBreakEnd().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 4 && workEnd.equals("-")) {
                    if (e.getWorkEnd() != null) {
                        workEnd = e.getWorkEnd().format(timeFormatter);
                    } else if (e.getEndTime() != null) {
                        workEnd = e.getEndTime().format(timeFormatter);
                    }
                }
                if (e.getDailyNote() != null && !e.getDailyNote().isEmpty() && dailyNote.isEmpty()) {
                    dailyNote = e.getDailyNote();
                }
            }
            String formattedDate = date.format(dateFormatter);
            report.add(new TimeReportDTO(
                    user.getUsername(),
                    formattedDate,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    dailyNote
            ));
        }
        return report;
    }
}
