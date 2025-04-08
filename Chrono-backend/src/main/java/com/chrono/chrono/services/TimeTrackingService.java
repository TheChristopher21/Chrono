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

        // Wenn noch nie gestempelt wurde => erlauben wir "WORK_START"
        // indem wir lastPunchOrder = 4 setzen.
        if (lastPunchOrder == null || lastPunchOrder == 0) {
            lastPunchOrder = 4;
        }

        if (!isTransitionAllowed(lastPunchOrder, newStatus)) {
            throw new RuntimeException("Transition not allowed from punchOrder = " + lastPunchOrder
                    + " to " + newStatus);
        }

        // Offenen Punch abschließen
        if (activePunch != null) {
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
            logger.info("handlePunch: Abgeschlossenes aktives Punch für User {}.", username);
        }

        int nextOrder = mapStatusToPunchOrder(newStatus);
        TimeTracking newEntry = createNewPunch(user, nextOrder);
        logger.info("handlePunch: Neuer Punch mit Order {} für User {} erstellt.", nextOrder, username);
        return convertToResponse(newEntry);
    }

    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        Optional<TimeTracking> lastCompleteTodayOpt = timeTrackingRepository
                .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(
                        user, startOfDay, endOfDay
                );
        if (lastCompleteTodayOpt.isPresent()) {
            TimeTracking lastCompleteToday = lastCompleteTodayOpt.get();
            if (lastCompleteToday.getPunchOrder() == 4) {
                throw new RuntimeException("Für heute wurde bereits ein kompletter Arbeitszyklus abgeschlossen.");
            }
        }

        Optional<TimeTracking> activePunchOpt =
                timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        if (activePunchOpt.isPresent()) {
            TimeTracking activePunch = activePunchOpt.get();
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
            logger.info("handleSmartPunch: Aktiver Punch für User {} abgeschlossen.", username);
        }

        Optional<TimeTracking> lastCompleteOpt = timeTrackingRepository
                .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(
                        user, startOfDay, endOfDay
                );
        Integer lastPunchOrder = lastCompleteOpt.map(TimeTracking::getPunchOrder).orElse(null);
        if (lastPunchOrder == null || lastPunchOrder == 0) {
            lastPunchOrder = 4;
        }

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
        return handlePunch(username, nextStatus);
    }

    /**
     * Diese Methode läuft täglich um 23:10 Uhr und schließt alle offenen Zeiteinträge
     * des aktuellen Tages automatisch, falls der Benutzer vergessen hat, auszustempeln.
     */
    @Scheduled(cron = "0 0 23 * * *") // Täglich um 23:00 Uhr
    @Transactional
    public void autoPunchOut() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime autoEndTime = today.atTime(23, 59, 59);

        // Hole alle TimeTracking-Einträge, die heute irgendwann begonnen wurden
        // und bei denen endTime noch null ist.
        // => So finden wir nur Nutzer, die IRGENDWAS gestempelt, aber nicht abgeschlossen haben.
        List<TimeTracking> openPunches = timeTrackingRepository
                .findByEndTimeIsNullAndStartTimeBetween(startOfDay, autoEndTime);
        logger.info("AutoPunchOut: Offen gefundene Einträge: {}", openPunches);

        // Gruppieren nach User
        Map<User, List<TimeTracking>> userMap = openPunches.stream()
                .collect(Collectors.groupingBy(TimeTracking::getUser));

        for (Map.Entry<User, List<TimeTracking>> entry : userMap.entrySet()) {
            User user = entry.getKey();

            // Alle heutigen Einträge – egal ob offen oder geschlossen,
            // damit wir sehen, was er bereits gestempelt hat.
            List<TimeTracking> entriesToday =
                    timeTrackingRepository.findByUserAndStartTimeBetween(user, startOfDay, autoEndTime);

            if (entriesToday.isEmpty()) {
                // => Der User hat heute gar nichts gestempelt.
                // => Nichts tun, weil du gesagt hast "nur eingreifen, wenn mind. einmal gestempelt wurde."
                logger.info("AutoPunchOut: {} hat GAR NICHT gestempelt. Mache nichts.", user.getUsername());
                continue;
            }

            // PunchOrders extrahieren
            Set<Integer> orders = entriesToday.stream()
                    .map(tt -> tt.getPunchOrder() != null ? tt.getPunchOrder() : 0)
                    .collect(Collectors.toSet());

            logger.info("AutoPunchOut: {} hat heutige PunchOrders: {}", user.getUsername(), orders);

            // Falls er schon 1..4 hat, also Set enthält [1,2,3,4], ist es "vollständig".
            if (orders.contains(1) && orders.contains(2) && orders.contains(3) && orders.contains(4)) {
                logger.info("AutoPunchOut: {} hat bereits vollen Zyklus (1..4). Nichts ergänzen.", user.getUsername());
                continue;
            }

            // => TEILWEISE gestempelt. Wir füllen fehlende Punches auf.
            for (int i = 1; i <= 4; i++) {
                final int punch = i; // <-- fix: "effektive" final-Variable
                if (!orders.contains(punch)) {
                    // Fehlender Punch => neu anlegen
                    TimeTracking created = new TimeTracking();
                    created.setUser(user);
                    created.setPunchOrder(punch);
                    // StartTime und ggf. EndTime auf autoEndTime plus offset
                    created.setStartTime(autoEndTime.plusSeconds(punch * 10));
                    if (punch == 3 || punch == 4) {
                        created.setEndTime(autoEndTime.plusSeconds(punch * 10));
                    }
                    created.setCorrected(true);
                    timeTrackingRepository.save(created);
                    logger.info("AutoPunchOut: {} => PunchOrder {} fehlte, neu erstellt.", user.getUsername(), punch);
                } else {
                    Optional<TimeTracking> openOpt = entriesToday.stream()
                            .filter(tt -> tt.getPunchOrder() == punch && tt.getEndTime() == null)
                            .findFirst();
                    if (openOpt.isPresent()) {
                        TimeTracking open = openOpt.get();
                        open.setEndTime(autoEndTime.plusSeconds(punch * 10));
                        open.setCorrected(true);
                        timeTrackingRepository.save(open);
                        logger.info("AutoPunchOut: {} => PunchOrder {} war offen, endTime gesetzt.", user.getUsername(), punch);
                    }
                }
            }
        }
    }

    /**
     * Hilfsmethode, die entweder einen neuen Punch anlegt oder – falls ein Eintrag mit der angegebenen punchOrder existiert,
     * dessen EndTime noch fehlt – diesen aktualisiert.
     */
    private void addOrUpdatePunch(User user, int punchOrder, LocalDateTime time) {
        Optional<TimeTracking> existingOpt = timeTrackingRepository.findTopByUserAndPunchOrder(user, punchOrder);
        if (existingOpt.isPresent()) {
            TimeTracking existing = existingOpt.get();
            if (existing.getEndTime() == null) {
                existing.setEndTime(time);
                existing.setCorrected(true);
                timeTrackingRepository.save(existing);
                logger.info("AutoPunchOut: Punch {} für User {} aktualisiert (EndTime gesetzt).", punchOrder, user.getUsername());
            } else {
                logger.info("AutoPunchOut: Punch {} für User {} existiert bereits und ist abgeschlossen.", punchOrder, user.getUsername());
            }
        } else {
            TimeTracking entry = new TimeTracking();
            entry.setUser(user);
            entry.setPunchOrder(punchOrder);
            entry.setStartTime(time);
            // Für Punch 3 und 4 setzen wir direkt auch EndTime, damit der Eintrag als abgeschlossen gilt.
            if (punchOrder == 3 || punchOrder == 4) {
                entry.setEndTime(time);
            }
            entry.setCorrected(true);
            timeTrackingRepository.save(entry);
            logger.info("AutoPunchOut: Neuer Punch {} für User {} angelegt (Time: {}).", punchOrder, user.getUsername(), time);
        }
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
        logger.info("updateDailyNote: Daily note für User {} für den Tag {} aktualisiert.", username, date);
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
                logger.info("updateDayTimeEntries: Self-edit detected for user {}.", targetUsername);
                if (!adminPassword.equals(userPassword)) {
                    throw new RuntimeException("For self-edit, admin and user passwords must match");
                }
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                logger.info("updateDayTimeEntries: Self-edit password match: adminPwdMatch = {}, userPwdMatch = {}", adminPwdMatch, userPwdMatch);
                if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
                if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
            } else {
                logger.info("updateDayTimeEntries: Editing entry of another user: {}", targetUsername);
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                logger.info("updateDayTimeEntries: Other edit password match: adminPwdMatch = {}, userPwdMatch = {}", adminPwdMatch, userPwdMatch);
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
        logger.info("updateDayTimeEntries: Deleted {} old entries for date {}", oldEntries.size(), date);

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
        logger.info("updateTimeTrackEntry: Called with id: {}", id);
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));
        User targetUser = tt.getUser();
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        String storedAdminPwd = adminUser.getAdminPassword();
        if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
            storedAdminPwd = adminUser.getPassword();
            logger.info("updateTimeTrackEntry: No separate admin password found; using login password.");
        }

        if (adminUsername.equals(targetUser.getUsername())) {
            logger.info("updateTimeTrackEntry: Self-edit detected for user {}", targetUser.getUsername());
            if (!adminPassword.equals(userPassword)) {
                throw new RuntimeException("For self-edit, admin and user passwords must be the same");
            }
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
            logger.info("updateTimeTrackEntry: Self-edit password match: adminPwdMatch = {}, userPwdMatch = {}", adminPwdMatch, userPwdMatch);
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
        } else {
            logger.info("updateTimeTrackEntry: Editing entry of another user: {}", targetUser.getUsername());
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
            logger.info("updateTimeTrackEntry: Other edit password match: adminPwdMatch = {}, userPwdMatch = {}", adminPwdMatch, userPwdMatch);
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
        }

        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);
        logger.info("updateTimeTrackEntry: Updated TimeTracking entry: {}", updated);

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
        if (lastPunchOrder == 0) {
            lastPunchOrder = null;
        }
        switch (newStatus) {
            case "WORK_START":
                return (lastPunchOrder == null || lastPunchOrder == 4);
            case "BREAK_START":
                return (lastPunchOrder != null && lastPunchOrder == 1);
            case "BREAK_END":
                return (lastPunchOrder != null && lastPunchOrder == 2);
            case "WORK_END":
                return (lastPunchOrder != null && (lastPunchOrder == 1 || lastPunchOrder == 3));
            default:
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
        logger.info("createNewPunch: Created new punch with order {} for user {} at {}.", punchOrder, user.getUsername(), now);
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
        LocalDate endDate   = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime   = endDate.plusDays(1).atStartOfDay();

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
