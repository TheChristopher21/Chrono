package com.chrono.chrono.controller;

import com.chrono.chrono.dto.PercentagePunchResponse;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Importiert für ResponseEntity
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Für Berechtigungen
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map; // Importiert für Map in ResponseEntity

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
    public TimeTrackingResponse punch(@RequestParam String username, Principal principal, @RequestHeader(value = "X-NFC-Agent-Request", required = false) String nfcAgentHeader) {
        // Sicherstellen, dass der Principal existiert, oder es eine valide NFC-Agent-Anfrage ist
        if (principal != null) {
            User requestingUser = userService.getUserByUsername(principal.getName());
            User targetUser = userService.getUserByUsername(username);
            // Es ist besser, eine spezifische Berechtigungsprüfung im Service oder via Spring Security zu haben.
            // Hier eine einfache Prüfung, ob sie zur selben Firma gehören, falls nicht SUPERADMIN.
            if (!requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) &&
                    (requestingUser.getCompany() == null || targetUser.getCompany() == null ||
                            !requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()))) {
                throw new SecurityException("Benutzer " + principal.getName() + " darf keinen Punch für Benutzer " + username + " einer anderen Firma durchführen.");
            }
        } else {
            // Kein Principal vorhanden, prüfe ob es eine erlaubte Anfrage ohne Principal ist
            // Erlaube "nfc-background-service" ODER wenn der spezielle Header vom NFC-Agenten vorhanden ist.
            // Der Wert des Headers "X-NFC-Agent-Request" könnte optional auch geprüft werden, falls ein Token verwendet wird.
            // Für diese Lösung reicht die Präsenz des Headers aus, um den NFC Agent zu identifizieren.
            System.out.println("NFC Agent Header X-NFC-Agent-Request: " + nfcAgentHeader); // Temporäres Logging

            boolean isNfcAgentCall = nfcAgentHeader != null && nfcAgentHeader.equals("true"); // Oder ein spezifischer Token-Wert
            boolean isAllowedInternalService = username.equals("nfc-background-service");

            if (!isAllowedInternalService && !isNfcAgentCall) {
                throw new SecurityException("Nicht autorisierter Punch-Versuch ohne Principal oder gültigen NFC-Agent-Header.");
            }
            // Wenn isNfcAgentCall true ist, wird die Anfrage vom NFC-Agenten als legitim betrachtet,
            // auch wenn `username` nicht "nfc-background-service" ist.
        }
        return timeTrackingService.handleSmartPunch(username);
    }

    @PostMapping("/work-start")
    public TimeTrackingResponse workStart(@RequestParam String username, Principal principal) {
        if (!principal.getName().equals(username)) {
            throw new SecurityException("Benutzer dürfen nur für sich selbst 'Work Start' stempeln.");
        }
        return timeTrackingService.handlePunch(username, "WORK_START");
    }

    @PostMapping("/break-start")
    public TimeTrackingResponse breakStart(@RequestParam String username, Principal principal) {
        if (!principal.getName().equals(username)) {
            throw new SecurityException("Benutzer dürfen nur für sich selbst 'Break Start' stempeln.");
        }
        return timeTrackingService.handlePunch(username, "BREAK_START");
    }

    @PostMapping("/break-end")
    public TimeTrackingResponse breakEnd(@RequestParam String username, Principal principal) {
        if (!principal.getName().equals(username)) {
            throw new SecurityException("Benutzer dürfen nur für sich selbst 'Break End' stempeln.");
        }
        return timeTrackingService.handlePunch(username, "BREAK_END");
    }

    @PostMapping("/work-end")
    public TimeTrackingResponse workEnd(@RequestParam String username, Principal principal) {
        if (!principal.getName().equals(username)) {
            throw new SecurityException("Benutzer dürfen nur für sich selbst 'Work End' stempeln.");
        }
        return timeTrackingService.handlePunch(username, "WORK_END");
    }

    @GetMapping("/history")
    public List<TimeTrackingResponse> getUserHistory(
            @RequestParam String username,
            Principal principal
    ) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);

        // Allow user to see their own history
        if (requestingUser.getId().equals(targetUser.getId())) {
            return timeTrackingService.getUserHistory(username);
        }
        // Allow admin/superadmin to see history of users in their company
        boolean isAdminOrSuperAdmin = requestingUser.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (isAdminOrSuperAdmin &&
                requestingUser.getCompany() != null &&
                targetUser.getCompany() != null &&
                requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
            return timeTrackingService.getUserHistory(username);
        }
        // Allow superadmin to see anyone's history
        if (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            return timeTrackingService.getUserHistory(username);
        }

        throw new SecurityException("Keine Berechtigung, die Zeiterfassung dieses Benutzers einzusehen.");
    }

    @GetMapping("/percentage-punch")
    public PercentagePunchResponse percentagePunch(
            @RequestParam String  username,
            @RequestParam double  percentage,
            Principal principal) {

        if (!principal.getName().equals(username)) {
            throw new SecurityException("Benutzer dürfen Percentage-Punch nur für sich selbst ausführen.");
        }
        User user = userService.getUserByUsername(username);
        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            throw new IllegalArgumentException("Benutzer " + username + " ist kein prozentualer Mitarbeiter.");
        }

        int expected = (int) Math.round(TimeTrackingService.FULL_DAY_MINUTES * (percentage / 100.0));
        int worked = timeTrackingService.getWorkedMinutes(user, LocalDate.now());
        int diff   = worked - expected;

        String msg = (diff >= 0)
                ? "Überstunden:  " +  diff + " min"
                : "Fehlstunden: " + (-diff) + " min";

        return new PercentagePunchResponse(username, worked, expected, diff, msg);
    }

    // Diese Methode ist im AdminTimeTrackingController, nicht hier.
    // @PutMapping("/editDay")
    // ...

    @PutMapping("/edit")
    @PreAuthorize("hasRole('USER')") // Assuming users can edit their own, or admins can edit via admin controller
    public ResponseEntity<?> editTimeTrackEntry(
            @RequestParam Long id,
            // @RequestParam String username, // username can be derived from Principal or already part of TimeTracking entry
            @RequestParam String date, // Date of the entry, might not be needed if ID is sufficient
            @RequestParam String workStart,
            @RequestParam String workEnd,
            // @RequestParam String adminUsername, // REMOVED - Admin actions go through AdminController
            // @RequestParam String adminPassword, // REMOVED
            @RequestParam(required = false) String userPassword, // Keep if users need to confirm their own edits
            Principal principal
    ) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            LocalTime ws = LocalTime.parse(workStart);
            LocalTime we = LocalTime.parse(workEnd);

            // The service method 'updateTimeTrackEntry' needs to be adjusted
            // to not expect adminPassword and to correctly handle userPassword for self-edits.
            TimeTrackingResponse response = timeTrackingService.updateTimeTrackEntry(
                    id,
                    parsedDate.atTime(ws),
                    parsedDate.atTime(we),
                    userPassword, // Pass for self-correction validation if applicable
                    principal.getName() // Pass the principal's name as the acting user (could be admin or self)
                    // If it's a self-edit, service needs to check if principal.getName() matches owner of entry 'id'
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Konnte Zeiteintrag nicht bearbeiten: " + e.getMessage()));
        }
    }


    @GetMapping("/report")
    public List<TimeReportDTO> getReport(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Principal principal
    ) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);

        if (!requestingUser.getId().equals(targetUser.getId())) {
            boolean isAdminOrSuperAdmin = requestingUser.getRoles().stream()
                    .anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));
            boolean isSuperAdmin = requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

            if (!isSuperAdmin && (!isAdminOrSuperAdmin || requestingUser.getCompany() == null || targetUser.getCompany() == null ||
                    !requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()))) {
                throw new SecurityException("Keine Berechtigung, den Bericht dieses Benutzers einzusehen.");
            }
        }
        return timeTrackingService.getReport(username, startDate, endDate);
    }

    @PostMapping("/daily-note")
    public TimeTrackingResponse updateDailyNote(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam String note,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            // Admins might be allowed to edit notes for users in their company
            User requestingUser = userService.getUserByUsername(principal.getName());
            User targetUser = userService.getUserByUsername(username);
            boolean isAdminOrSuperAdmin = requestingUser.getRoles().stream()
                    .anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));

            if (!isAdminOrSuperAdmin || requestingUser.getCompany() == null || targetUser.getCompany() == null ||
                    !requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                throw new SecurityException("Keine Berechtigung, die Notiz für diesen Benutzer zu ändern.");
            }
        }
        return timeTrackingService.updateDailyNote(username, date, note);
    }

    @GetMapping("/work-difference")
    public int getWorkDifference(
            @RequestParam String username,
            @RequestParam String date,
            Principal principal) {

        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!requestingUser.getId().equals(targetUser.getId())) {
            boolean isAdminOrSuperAdmin = requestingUser.getRoles().stream()
                    .anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));
            boolean isSuperAdmin = requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));


            if (!isSuperAdmin && (!isAdminOrSuperAdmin || requestingUser.getCompany() == null || targetUser.getCompany() == null ||
                    !requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()))) {
                throw new SecurityException("Keine Berechtigung, die Arbeitsdifferenz dieses Benutzers einzusehen.");
            }
        }
        return timeTrackingService.computeDailyWorkDifference(targetUser, LocalDate.parse(date));
    }
}