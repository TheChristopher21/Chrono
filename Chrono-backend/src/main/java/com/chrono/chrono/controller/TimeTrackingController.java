package com.chrono.chrono.controller;

import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hier ruft dein Frontend die Endpunkte auf.
 * Die eigentliche Logik (Korrekturen, autoPunchOut usw.) ist in TimeTrackingService.
 */
@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private WorkScheduleService workScheduleService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/punch")
    public TimeTrackingResponse punch(@RequestParam String username) {
        return timeTrackingService.handleSmartPunch(username);
    }

    @PostMapping("/work-start")
    public TimeTrackingResponse workStart(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "WORK_START");
    }

    @PostMapping("/break-start")
    public TimeTrackingResponse breakStart(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "BREAK_START");
    }

    @PostMapping("/break-end")
    public TimeTrackingResponse breakEnd(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "BREAK_END");
    }

    @PostMapping("/work-end")
    public TimeTrackingResponse workEnd(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "WORK_END");
    }

    @GetMapping("/history")
    public List<TimeTrackingResponse> getUserHistory(@RequestParam String username) {
        return timeTrackingService.getUserHistory(username);
    }

    /**
     * Endpunkt für Korrekturanträge: Aktualisiert die Zeitstempel für einen Tag.
     * Es werden alle alten Einträge des Tages gelöscht und durch die neuen ersetzt.
     * Es wird erwartet, dass alle vier Zeitwerte (Work Start, Break Start, Break End, Work End) übermittelt werden.
     */
    @PutMapping("/editDay")
    public String editDayTimeEntries(
            @RequestParam String targetUsername,
            @RequestParam String date,
            @RequestParam String workStart,
            @RequestParam String breakStart,
            @RequestParam String breakEnd,
            @RequestParam String workEnd,
            @RequestParam String adminUsername,
            @RequestParam String adminPassword,
            @RequestParam String userPassword
    ) {
        return timeTrackingService.updateDayTimeEntries(
                targetUsername,
                date,
                workStart,
                breakStart,
                breakEnd,
                workEnd,
                adminUsername,
                adminPassword,
                userPassword
        );
    }

    @PutMapping("/edit")
    public AdminTimeTrackDTO editTimeTrackEntry(
            @RequestParam Long id,
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam String workStart,
            @RequestParam String breakStart,
            @RequestParam String breakEnd,
            @RequestParam String workEnd,
            @RequestParam String adminUsername,
            @RequestParam String adminPassword,
            @RequestParam String userPassword
    ) {
        LocalDate parsedDate = LocalDate.parse(date);
        LocalDateTime newStart = parsedDate.atTime(java.time.LocalTime.parse(workStart));
        LocalDateTime newEnd = parsedDate.atTime(java.time.LocalTime.parse(workEnd));
        return timeTrackingService.updateTimeTrackEntry(id, newStart, newEnd, userPassword, adminUsername, adminPassword);
    }

    @GetMapping("/report")
    public List<TimeReportDTO> getReport(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return timeTrackingService.getReport(username, startDate, endDate);
    }

    /**
     * Neuer Endpunkt: Aktualisiert die tägliche Notiz (dailyNote)
     * für einen stundenbasierten Nutzer an einem bestimmten Datum.
     */
    @PostMapping("/daily-note")
    public TimeTrackingResponse updateDailyNote(@RequestParam String username,
                                                @RequestParam String date,
                                                @RequestParam String note) {
        return timeTrackingService.updateDailyNote(username, date, note);
    }

    @GetMapping("/work-difference")
    public int getWorkDifference(@RequestParam String username, @RequestParam String date) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate parsedDate = LocalDate.parse(date);

        int expectedMinutes = user.isHourly() ? 0 : workScheduleService.computeExpectedWorkMinutes(user, parsedDate);

        LocalDateTime dayStart = parsedDate.atStartOfDay();
        LocalDateTime dayEnd = parsedDate.plusDays(1).atStartOfDay();
        List<TimeTracking> entries = timeTrackingService.getTimeTrackingEntriesForUserAndDate(user, dayStart, dayEnd);

        TimeTracking workStartEntry = entries.stream()
                .filter(e -> e.getPunchOrder() == 1)
                .findFirst()
                .orElse(null);
        TimeTracking workEndEntry = entries.stream()
                .filter(e -> e.getPunchOrder() == 4)
                .findFirst()
                .orElse(null);

        int actualMinutes = 0;
        if (workStartEntry != null && workEndEntry != null) {
            LocalDateTime start = workStartEntry.getStartTime();
            LocalDateTime end = workEndEntry.getEndTime();
            if (user.isHourly()) {
                if (start.toLocalDate().equals(end.toLocalDate())) {
                    actualMinutes = (int) ChronoUnit.MINUTES.between(start, end);
                } else {
                    LocalDateTime midnight = start.toLocalDate().plusDays(1).atStartOfDay();
                    actualMinutes = (int) ChronoUnit.MINUTES.between(start, midnight);
                }
            } else {
                actualMinutes = (int) ChronoUnit.MINUTES.between(start, end);
            }
        }
        return actualMinutes - expectedMinutes;
    }
}
