// Datei: src/main/java/com/chrono/chrono/controller/VacationController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.VacationService;
import org.slf4j.Logger; // SLF4J Logger importieren
import org.slf4j.LoggerFactory; // SLF4J Logger importieren
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vacation")
public class VacationController {

    private static final Logger logger = LoggerFactory.getLogger(VacationController.class); // Logger Instanz

    @Autowired
    private VacationService vacationService;

    @Autowired
    private UserService userService;

    @GetMapping("/user/{username}")
    @PreAuthorize("#username == principal.name or hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<List<VacationRequest>> getUserVacations(@PathVariable String username) {
        return ResponseEntity.ok(vacationService.getUserVacations(username));
    }
    @PostMapping("/create")
    public ResponseEntity<?> createVacation(@RequestParam String username,
                                            @RequestParam String startDate,
                                            @RequestParam String endDate,
                                            @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                                            @RequestParam(required = false, defaultValue = "false") boolean usesOvertime,
                                            @RequestParam(required = false) Integer overtimeDeductionMinutes,
                                            Principal principal) {
        if (!principal.getName().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Benutzer dürfen nur für sich selbst Urlaub beantragen."));
        }
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            VacationRequest request = vacationService.createVacationRequest(username, start, end, halfDay, usesOvertime, overtimeDeductionMinutes);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException in createVacation for user {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createVacation for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fehler beim Erstellen des Urlaubsantrags: " + e.getMessage()));
        }
    }

    @PostMapping("/adminCreate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> adminCreateVacation(@RequestParam String adminUsername,
                                                 @RequestParam String username, // targetUsername
                                                 @RequestParam String startDate,
                                                 @RequestParam String endDate,
                                                 @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                                                 @RequestParam(required = false, defaultValue = "false") boolean usesOvertime,
                                                 @RequestParam(required = false) Integer overtimeDeductionMinutes,
                                                 Principal principal) {
        if (!principal.getName().equals(adminUsername)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Aktion nicht erlaubt. Admin-Username stimmt nicht mit angemeldetem User überein."));
        }
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            VacationRequest request = vacationService.adminCreateVacation(adminUsername, username, start, end, halfDay, usesOvertime, overtimeDeductionMinutes);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException in adminCreateVacation for targetUser {} by admin {}: {}", username, adminUsername, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) { // Spezifisches Abfangen der SecurityException
            logger.warn("SecurityException in adminCreateVacation for targetUser {} by admin {}: {}", username, adminUsername, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) { // Für andere RuntimeExceptions
            logger.error("RuntimeException in adminCreateVacation for targetUser {} by admin {}: {}", username, adminUsername, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fehler beim Erstellen des Urlaubsantrags durch Admin: " + e.getMessage()));
        }
    }

    @PostMapping("/companyCreate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> adminCreateCompanyVacation(@RequestParam String adminUsername,
                                                        @RequestParam String startDate,
                                                        @RequestParam String endDate,
                                                        @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                                                        Principal principal) {
        if (!principal.getName().equals(adminUsername)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Aktion nicht erlaubt. Admin-Username stimmt nicht mit angemeldetem User überein."));
        }
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            List<VacationRequest> created = vacationService.adminCreateCompanyVacation(adminUsername, start, end, halfDay);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException in adminCreateCompanyVacation by admin {}: {}", adminUsername, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            logger.warn("SecurityException in adminCreateCompanyVacation by admin {}: {}", adminUsername, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("RuntimeException in adminCreateCompanyVacation by admin {}: {}", adminUsername, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fehler beim Erstellen des Betriebsurlaubs: " + e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<VacationRequest>> getMyVacations(Principal principal) {
        return ResponseEntity.ok(vacationService.getUserVacations(principal.getName()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAllVacations(Principal principal) {
        User adminUser = userService.getUserByUsername(principal.getName());
        if (adminUser.getCompany() == null && !adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            // Admin (nicht SUPERADMIN) ohne Firma darf keine Firmen-Urlaube sehen.
            logger.warn("Admin user {} has no company assigned and is not SUPERADMIN. Cannot fetch all vacations.", adminUser.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin user " + adminUser.getUsername() + " hat keine Firma zugewiesen und ist kein SUPERADMIN."));
        }
        // SUPERADMIN kann alle sehen (companyId wird dann ggf. ignoriert oder speziell behandelt im Service)
        // Regulärer Admin sieht nur die seiner Firma
        Long companyIdToFetch = adminUser.getCompany() != null ? adminUser.getCompany().getId() : null;
        if (adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) && companyIdToFetch == null) {
            // Wenn SUPERADMIN und keine spezifische Firma ausgewählt wurde (z.B. weil Admin keiner Firma zugeordnet ist),
            // dann alle Urlaube von allen Firmen laden (oder eine leere Liste, je nach Anforderung).
            // Aktuell würde getAllVacationsInCompany mit null companyId eine leere Liste liefern.
            // Für einen Superadmin könnte man hier `vacationService.getAllVacations()` aufrufen.
            logger.info("SUPERADMIN {} fetching all vacations from all companies.", adminUser.getUsername());
            return ResponseEntity.ok(vacationService.getAllVacations()); // SUPERADMIN sieht alle
        }
        logger.info("Admin {} fetching vacations for companyId: {}", adminUser.getUsername(), companyIdToFetch);
        return ResponseEntity.ok(vacationService.getAllVacationsInCompany(companyIdToFetch));
    }

    @PostMapping("/approve/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, Principal principal) {
        try {
            VacationRequest request = vacationService.approveVacation(id, principal.getName());
            return ResponseEntity.ok(request);
        } catch (SecurityException e) {
            logger.warn("SecurityException in approve vacation for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("RuntimeException in approve vacation for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/deny/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> deny(@PathVariable Long id, Principal principal) {
        try {
            VacationRequest request = vacationService.denyVacation(id, principal.getName());
            return ResponseEntity.ok(request);
        } catch (SecurityException e) {
            logger.warn("SecurityException in deny vacation for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("RuntimeException in deny vacation for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> deleteVacation(@PathVariable Long id,
                                            @RequestParam String adminUsername,
                                            Principal principal) {
        if (!principal.getName().equals(adminUsername)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Aktion nicht erlaubt. Admin-Username stimmt nicht mit angemeldetem User überein."));
        }
        try {
            vacationService.deleteVacation(id, adminUsername);
            return ResponseEntity.ok(Map.of("message", "Urlaubsantrag erfolgreich gelöscht."));
        } catch (SecurityException e) {
            logger.warn("SecurityException in deleteVacation for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("RuntimeException in deleteVacation for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> updateVacation(@PathVariable Long id,
                                            @RequestBody VacationUpdateRequest body,
                                            Principal principal) {
        try {
            LocalDate start = body.getStartDate() != null ? LocalDate.parse(body.getStartDate()) : null;
            LocalDate end = body.getEndDate() != null ? LocalDate.parse(body.getEndDate()) : null;

            VacationRequest updated = vacationService.adminUpdateVacation(
                    id,
                    principal.getName(),
                    start,
                    end,
                    body.getHalfDay(),
                    body.getUsesOvertime(),
                    body.getOvertimeDeductionMinutes(),
                    body.getApproved(),
                    body.getDenied());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException in updateVacation for id {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            logger.warn("SecurityException in updateVacation for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("RuntimeException in updateVacation for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fehler beim Aktualisieren des Urlaubsantrags: " + e.getMessage()));
        }
    }

    public static class VacationUpdateRequest {
        private String startDate;
        private String endDate;
        private Boolean halfDay;
        private Boolean usesOvertime;
        private Integer overtimeDeductionMinutes;
        private Boolean approved;
        private Boolean denied;

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public Boolean getHalfDay() {
            return halfDay;
        }

        public void setHalfDay(Boolean halfDay) {
            this.halfDay = halfDay;
        }

        public Boolean getUsesOvertime() {
            return usesOvertime;
        }

        public void setUsesOvertime(Boolean usesOvertime) {
            this.usesOvertime = usesOvertime;
        }

        public Integer getOvertimeDeductionMinutes() {
            return overtimeDeductionMinutes;
        }

        public void setOvertimeDeductionMinutes(Integer overtimeDeductionMinutes) {
            this.overtimeDeductionMinutes = overtimeDeductionMinutes;
        }

        public Boolean getApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public Boolean getDenied() {
            return denied;
        }

        public void setDenied(Boolean denied) {
            this.denied = denied;
        }
    }

    @GetMapping("/remaining")
    public ResponseEntity<?> getRemainingVacation(@RequestParam String username, @RequestParam int year, Principal principal) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);

        boolean isSuperAdmin = requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        boolean isAdminOfSameCompany = false;
        if (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
            if (requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                    requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                isAdminOfSameCompany = true;
            }
        }

        if (!requestingUser.getUsername().equals(username) && !isSuperAdmin && !isAdminOfSameCompany) {
            logger.warn("User {} tried to access remaining vacation days for {} without permission.", principal.getName(), username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Sie haben keine Berechtigung, diese Informationen anzuzeigen."));
        }

        try {
            double remainingDays = vacationService.calculateRemainingVacationDays(username, year);
            return ResponseEntity.ok(remainingDays);
        } catch (RuntimeException e) {
            logger.error("Error calculating remaining vacation for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }
}