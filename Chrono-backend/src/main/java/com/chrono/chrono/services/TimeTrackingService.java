package com.chrono.chrono.services;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Erstellt einen neuen Punch-Eintrag für den Benutzer anhand des gewünschten Status.
     */
    public TimeTrackingResponse handlePunch(String username, String newStatus) {
        // Benutzer laden
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        // Aktiven Punch (ohne Endzeit) ermitteln
        Optional<TimeTracking> activePunchOpt = timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        TimeTracking activePunch = activePunchOpt.orElse(null);

        // Letzten Eintrag laden, um den letzten punchOrder zu bestimmen
        TimeTracking lastEntry = timeTrackingRepository.findTopByUserOrderByIdDesc(user).orElse(null);
        Integer lastPunchOrder = (activePunch != null)
                ? activePunch.getPunchOrder()
                : (lastEntry != null ? lastEntry.getPunchOrder() : null);
        if (lastPunchOrder == null || lastPunchOrder == 0) {
            lastPunchOrder = 4; // Standard: WORK_START darf erfolgen
        }

        if (!isTransitionAllowed(lastPunchOrder, newStatus)) {
            throw new RuntimeException("Transition not allowed from punchOrder = " + lastPunchOrder + " to " + newStatus);
        }

        // Aktiven Eintrag beenden, falls vorhanden
        if (activePunch != null) {
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
        }

        int nextOrder = mapStatusToPunchOrder(newStatus);
        TimeTracking newEntry = createNewPunch(user, nextOrder);
        return convertToResponse(newEntry);
    }

    /**
     * Ermittelt den nächsten Punch-Status basierend auf den Einträgen von heute und ruft handlePunch auf.
     */
    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        Optional<TimeTracking> lastCompleteTodayOpt = timeTrackingRepository
                .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(user, startOfDay, endOfDay);
        if (lastCompleteTodayOpt.isPresent()) {
            TimeTracking lastCompleteToday = lastCompleteTodayOpt.get();
            if (lastCompleteToday.getPunchOrder() == 4) {
                throw new RuntimeException("Für heute wurde bereits ein kompletter Arbeitszyklus abgeschlossen.");
            }
        }

        // Aktiven Eintrag beenden
        Optional<TimeTracking> activePunchOpt = timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        if (activePunchOpt.isPresent()) {
            TimeTracking activePunch = activePunchOpt.get();
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.saveAndFlush(activePunch);
        }

        Optional<TimeTracking> lastCompleteOpt = timeTrackingRepository
                .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(user, startOfDay, endOfDay);
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
     * Aktualisiert alle TimeTracking-Einträge eines bestimmten Tages für den Zieluser.
     * Dabei werden die Passwörter überprüft: Der aktuell authentifizierte Admin (mit seinem
     * Admin-Passwort oder, falls nicht vorhanden, Login-Passwort) und das vom Zieluser eingegebene Passwort.
     */
    @Transactional
    public String updateDayTimeEntries(String targetUsername,
                                       String date,
                                       String workStartStr,
                                       String breakStartStr,
                                       String breakEndStr,
                                       String workEndStr,
                                       String adminUsername,
                                       String adminPassword,
                                       String userPassword) {
        System.out.println("DEBUG: updateDayTimeEntries called with parameters:");
        System.out.println("DEBUG: targetUsername: " + targetUsername);
        System.out.println("DEBUG: date: " + date);
        System.out.println("DEBUG: workStart: " + workStartStr);
        System.out.println("DEBUG: breakStart: " + breakStartStr);
        System.out.println("DEBUG: breakEnd: " + breakEndStr);
        System.out.println("DEBUG: workEnd: " + workEndStr);
        System.out.println("DEBUG: adminUsername: " + adminUsername);
        System.out.println("DEBUG: adminPassword (input): " + adminPassword);
        System.out.println("DEBUG: userPassword (input): " + userPassword);

        // 1) Benutzer laden
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        // 2) Admin-Passwort prüfen (separates Admin-Passwort oder fallback auf Login-PW)
        String storedAdminPwd = adminUser.getAdminPassword();
        if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
            storedAdminPwd = adminUser.getPassword();
            System.out.println("DEBUG: No separate admin password found, using login password");
        }

        // 3) Passwörter matchen
        if (targetUsername.equals(adminUsername)) {
            // Self-edit: adminPassword == userPassword
            if (!adminPassword.equals(userPassword)) {
                throw new RuntimeException("For self-edit, admin and user passwords must match");
            }
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
        } else {
            // Fremd-Edit: Admin und User
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
            if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
            if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
        }

        // 4) Datum/Zeit parsen
        LocalDate parsedDate = LocalDate.parse(date);
        LocalTime newWorkStart = LocalTime.parse(workStartStr);
        LocalTime newWorkEnd   = LocalTime.parse(workEndStr);

        // Pausenlogik: Falls breakStart/breakEnd == "00:00", interpretieren wir das als keine Pause
        boolean hasBreak = !(breakStartStr.equals("00:00") && breakEndStr.equals("00:00"));
        LocalTime newBreakStart = hasBreak ? LocalTime.parse(breakStartStr) : null;
        LocalTime newBreakEnd   = hasBreak ? LocalTime.parse(breakEndStr)   : null;

        LocalDateTime newWorkStartDT  = parsedDate.atTime(newWorkStart);
        LocalDateTime newWorkEndDT    = parsedDate.atTime(newWorkEnd);
        LocalDateTime newBreakStartDT = hasBreak ? parsedDate.atTime(newBreakStart) : null;
        LocalDateTime newBreakEndDT   = hasBreak ? parsedDate.atTime(newBreakEnd)   : null;

        // 5) Zeitfenster definieren und ALTE Einträge entfernen
        LocalDateTime dayStart = parsedDate.atStartOfDay();             // z.B. 2025-03-13 00:00
        LocalDateTime dayEnd   = parsedDate.plusDays(1).atStartOfDay(); // 2025-03-14 00:00

        List<TimeTracking> oldEntries = timeTrackingRepository.findByUserAndStartTimeBetween(targetUser, dayStart, dayEnd);
        // Falls Sie ganz sicher sein wollen, dass auch Einträge gelöscht werden, die evtl. "über die Mitternacht" hinausragen,
        // müssten Sie eine Query verwenden, die auch endTime prüft. Für Standardfälle reicht dies oft aus.
        timeTrackingRepository.deleteAll(oldEntries);
        System.out.println("DEBUG: Deleted " + oldEntries.size() + " old entries for " + date);

        // 6) Neue Einträge anlegen

        // (a) Work Start (PunchOrder = 1)
        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);
        System.out.println("DEBUG: Created Work Start: " + ts1);

        // (b) Break Start (PunchOrder = 2)
        if (hasBreak) {
            TimeTracking ts2 = new TimeTracking();
            ts2.setUser(targetUser);
            ts2.setPunchOrder(2);
            ts2.setStartTime(newBreakStartDT);
            ts2.setBreakStart(newBreakStart);
            ts2.setCorrected(true);
            timeTrackingRepository.save(ts2);
            System.out.println("DEBUG: Created Break Start: " + ts2);
        }

        // (c) Break End (PunchOrder = 3)
        if (hasBreak) {
            TimeTracking ts3 = new TimeTracking();
            ts3.setUser(targetUser);
            ts3.setPunchOrder(3);
            ts3.setStartTime(newBreakEndDT);
            ts3.setBreakEnd(newBreakEnd);
            ts3.setCorrected(true);
            timeTrackingRepository.save(ts3);
            System.out.println("DEBUG: Created Break End: " + ts3);
        }

        // (d) Work End (PunchOrder = 4)
        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);
        System.out.println("DEBUG: Created Work End: " + ts4);

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

    public AdminTimeTrackDTO updateTimeTrackEntry(Long id,
                                                  LocalDateTime newStart,
                                                  LocalDateTime newEnd,
                                                  String userPassword,
                                                  String adminUsername,
                                                  String adminPassword) {
        System.out.println("DEBUG: updateTimeTrackEntry called with id: " + id);
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));
        User targetUser = tt.getUser();
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        String storedAdminPwd = adminUser.getAdminPassword();
        if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
            storedAdminPwd = adminUser.getPassword();
            System.out.println("DEBUG: No separate admin password found for admin user, using login password");
        }

        if (adminUsername.equals(targetUser.getUsername())) {
            System.out.println("DEBUG: Self-edit in updateTimeTrackEntry");
            if (!adminPassword.equals(userPassword)) {
                throw new RuntimeException("For self-edit, admin and user passwords must be the same");
            }
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch = passwordEncoder.matches(userPassword, targetUser.getPassword());
            System.out.println("DEBUG: Self-edit password match: adminPwdMatch = " + adminPwdMatch + ", userPwdMatch = " + userPwdMatch);
            if (!adminPwdMatch) {
                throw new RuntimeException("Invalid admin password");
            }
            if (!userPwdMatch) {
                throw new RuntimeException("Invalid user password");
            }
        } else {
            System.out.println("DEBUG: Editing entry of another user");
            boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
            boolean userPwdMatch = passwordEncoder.matches(userPassword, targetUser.getPassword());
            System.out.println("DEBUG: Other edit password match: adminPwdMatch = " + adminPwdMatch + ", userPwdMatch = " + userPwdMatch);
            if (!adminPwdMatch) {
                throw new RuntimeException("Invalid admin password");
            }
            if (!userPwdMatch) {
                throw new RuntimeException("Invalid user password");
            }
        }

        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);
        System.out.println("DEBUG: TimeTracking entry updated: " + updated);

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
        return timeTrackingRepository.save(tt);
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
            default:
                label = "Unknown";
        }
        return new TimeTrackingResponse(
                tt.getId(),
                tt.getStartTime(),
                tt.getEndTime(),
                tt.isCorrected(),
                label,
                pOrder
        );
    }

    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<TimeTracking> entries = timeTrackingRepository.findByUserAndStartTimeBetween(user, startDateTime, endDateTime);

        Map<LocalDate, List<TimeTracking>> grouped = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getStartTime().toLocalDate()));

        List<TimeReportDTO> report = new ArrayList<>();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedDates);

        for (LocalDate date : sortedDates) {
            List<TimeTracking> dayEntries = grouped.get(date);
            dayEntries.sort(Comparator.comparingInt(e -> e.getPunchOrder() != null ? e.getPunchOrder() : 0));

            String workStart = "-";
            String breakStart = "-";
            String breakEnd = "-";
            String workEnd = "-";

            for (TimeTracking e : dayEntries) {
                int order = e.getPunchOrder() != null ? e.getPunchOrder() : 0;
                if (order == 1 && workStart.equals("-")) {
                    workStart = e.getWorkStart() != null ? e.getWorkStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 2 && breakStart.equals("-")) {
                    breakStart = e.getBreakStart() != null ? e.getBreakStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 3 && breakEnd.equals("-")) {
                    breakEnd = e.getBreakEnd() != null ? e.getBreakEnd().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 4 && workEnd.equals("-")) {
                    if (e.getWorkEnd() != null) {
                        workEnd = e.getWorkEnd().format(timeFormatter);
                    } else if (e.getEndTime() != null) {
                        workEnd = e.getEndTime().format(timeFormatter);
                    }
                }
            }
            String formattedDate = date.format(dateFormatter);
            report.add(new TimeReportDTO(user.getUsername(), formattedDate, workStart, breakStart, breakEnd, workEnd));
        }
        return report;
    }
}