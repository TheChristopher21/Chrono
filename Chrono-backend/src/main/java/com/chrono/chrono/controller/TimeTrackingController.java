package com.chrono.chrono.controller;

import com.chrono.chrono.dto.PercentagePunchResponse;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.WorkScheduleService;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hier ruft dein Frontend die Endpunkte auf.
 * Die eigentliche Logik (Korrekturen, autoPunchOut usw.) ist in TimeTrackingService.
 */
@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    @Autowired
    private UserService userService; // NEU

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private WorkScheduleService workScheduleService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Smart-Punch (autodetektiert WORK_START, BREAK_START, BREAK_END, WORK_END),
     * prüft u.a. ob bei isPercentage=false schon WORK_END am heutigen Tag existiert.
     */
    @PostMapping("/punch")
    public TimeTrackingResponse punch(@RequestParam String username, Principal principal) {

        // Nur prüfen, wenn wirklich jemand eingeloggtes stempelt
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

    /**
     * Liefert die komplette Stempelhistorie (Liste der TimeTrackingResponse)
     * nach absteigendem StartTime sortiert.
     */
    @GetMapping("/history")
    public List<TimeTrackingResponse> getUserHistory(
            @RequestParam String username,
            Principal principal
    ) {
        // 1) Hole den anfragenden User => check Firma
        User requestingUser = userService.getUserByUsername(principal.getName());
        // 2) Hole den targetUser => check, ob company übereinstimmt
        User targetUser = userService.getUserByUsername(username);
        if (!requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
            throw new RuntimeException("Keine Berechtigung auf fremde Firma");
        }
        return timeTrackingService.getUserHistory(username);
    }
    /**
     * Reine Tagesberechnung: ermittelt, wie viele Minuten der User heute
     * gearbeitet hat und wie viele er laut 'percentage' haben sollte.
     */
    @GetMapping("/percentage-punch")
    public PercentagePunchResponse percentagePunch(
            @RequestParam String username,
            @RequestParam double percentage
    ) {
        return timeTrackingService.handlePercentagePunch(username, percentage);
    }

    /**
     * Korrigiert den gesamten Tag (alle Einträge) auf workStart, breakStart, breakEnd, workEnd.
     * Alte Einträge für diesen Tag werden gelöscht und neu angelegt.
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

    /**
     * Korrigiert einen einzelnen TimeTracking-Datensatz (per ID),
     * indem Start-/EndTime gesetzt werden (z.B. aus dem Admin-Panel).
     */
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
        return timeTrackingService.updateTimeTrackEntry(
                id, newStart, newEnd, userPassword, adminUsername, adminPassword
        );
    }

    /**
     * Erstellt einen Bericht über Start-/Endzeiten, Pausen und DailyNotes
     * in dem angegebenen Datumsbereich (z.B. für PDF-Ausdruck).
     */
    @GetMapping("/report")
    public List<TimeReportDTO> getReport(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        return timeTrackingService.getReport(username, startDate, endDate);
    }

    /**
     * Fügt oder aktualisiert für stundenbasierte Mitarbeiter eine Tages-Notiz.
     */
    @PostMapping("/daily-note")
    public TimeTrackingResponse updateDailyNote(@RequestParam String username,
                                                @RequestParam String date,
                                                @RequestParam String note) {
        return timeTrackingService.updateDailyNote(username, date, note);
    }

    /**
     * Beispiel: Berechnet die Differenz zwischen Ist- und Soll-Zeit an einem Tag
     * (wird je nach isPercentage, isHourly etc. anders berechnet).
     */
    @GetMapping("/work-difference")
    public int getWorkDifference(@RequestParam String username, @RequestParam String date) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return timeTrackingService.computeDailyWorkDifference(user, date);
    }
}
