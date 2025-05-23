package com.chrono.chrono.controller;

import com.chrono.chrono.dto.PercentagePunchResponse;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    @Autowired
    private UserService userService;

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private UserRepository userRepository;

    /**
     * "Smart-Punch" => autom. 1->2->3->4
     */
    @PostMapping("/punch")
    public TimeTrackingResponse punch(@RequestParam String username, Principal principal) {
        if (principal != null) {
            userService.assertSameCompany(principal.getName(), username);
        }
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
    public List<TimeTrackingResponse> getUserHistory(
            @RequestParam String username,
            Principal principal
    ) {
        // Firma prüfen
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);
        if (!requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
            throw new RuntimeException("Keine Berechtigung auf fremde Firma");
        }
        return timeTrackingService.getUserHistory(username);
    }

    @GetMapping("/percentage-punch")
    public PercentagePunchResponse percentagePunch(
            @RequestParam String username,
            @RequestParam double percentage
    ) {
        // Bsp: 8h30 * (percentage/100)
        int expected = (int)Math.round(TimeTrackingService.FULL_DAY_MINUTES * (percentage / 100.0));
        User user = userService.getUserByUsername(username);

        int worked = timeTrackingService.computeDailyWorkDifference(user, LocalDate.now().toString());
        int diff = worked - expected;

        String msg = (diff >= 0)
                ? "Überstunden: " + diff + " min"
                : "Fehlstunden: " + (-diff) + " min";

        return new PercentagePunchResponse(username, worked, expected, diff, msg);
    }

    @PutMapping("/editDay")
    public ResponseEntity<?> editDayTimeEntries(
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
        try {
            String result = timeTrackingService.updateDayTimeEntries(
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
            return ResponseEntity.ok(result);

        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            if (msg.contains("Invalid admin password")
                    || msg.contains("Invalid user password")
                    || msg.contains("For self-edit, admin and user passwords must match")) {
                return ResponseEntity.status(403).body("Passwort-Fehler: " + msg);
            }
            return ResponseEntity.badRequest()
                    .body("Konnte dayTimeEntries nicht bearbeiten: " + msg);
        }
    }

    @PutMapping("/edit")
    public TimeTrackingResponse editTimeTrackEntry(
            @RequestParam Long id,
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam String workStart,
            @RequestParam String workEnd,
            @RequestParam String adminUsername,
            @RequestParam String adminPassword,
            @RequestParam String userPassword
    ) {
        LocalDate parsedDate = LocalDate.parse(date);
        LocalTime ws = LocalTime.parse(workStart);
        LocalTime we = LocalTime.parse(workEnd);

        return timeTrackingService.updateTimeTrackEntry(
                id,
                parsedDate.atTime(ws),
                parsedDate.atTime(we),
                userPassword,
                adminUsername,
                adminPassword
        );
    }

    @GetMapping("/report")
    public List<TimeReportDTO> getReport(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        return timeTrackingService.getReport(username, startDate, endDate);
    }

    @PostMapping("/daily-note")
    public TimeTrackingResponse updateDailyNote(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam String note
    ) {
        return timeTrackingService.updateDailyNote(username, date, note);
    }

    @GetMapping("/work-difference")
    public int getWorkDifference(
            @RequestParam String username,
            @RequestParam String date
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return timeTrackingService.computeDailyWorkDifference(user, date);
    }
}
