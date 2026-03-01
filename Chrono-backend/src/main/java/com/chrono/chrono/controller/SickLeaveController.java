package com.chrono.chrono.controller;

import com.chrono.chrono.entities.SickLeave;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.services.SickLeaveService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sick-leave")
public class SickLeaveController {

    @Autowired
    private SickLeaveService sickLeaveService;

    @Autowired
    private UserService userService;

    @PostMapping("/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> reportSickLeave(
            @RequestParam String targetUsername,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "false") boolean halfDay,
            @RequestParam(required = false) String comment,
            Principal principal) {
        try {
            SickLeave sickLeave = sickLeaveService.reportSickLeave(principal.getName(), targetUsername, startDate, endDate, halfDay, comment);
            return ResponseEntity.status(HttpStatus.CREATED).body(sickLeave);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Fehler beim Melden der Krankheit: " + e.getMessage()));
        }
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SickLeave>> getMySickLeaves(Principal principal) {
        List<SickLeave> sickLeaves = sickLeaveService.getSickLeavesForUser(principal.getName());
        return ResponseEntity.ok(sickLeaves);
    }

    @GetMapping("/user/{username}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getSickLeavesForUserByAdmin(
            @PathVariable String username,
            Principal principal) {
        try {
            User admin = userService.getUserByUsername(principal.getName());
            User targetUser = userService.getUserByUsername(username);

            if (!admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                if (admin.getCompany() == null || targetUser.getCompany() == null ||
                        !admin.getCompany().getId().equals(targetUser.getCompany().getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Admin hat keine Berechtigung für diesen Benutzer."));
                }
            }
            List<SickLeave> sickLeaves = sickLeaveService.getSickLeavesForUser(username);
            return ResponseEntity.ok(sickLeaves);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<List<SickLeave>> getAllSickLeavesInCompany(
            @RequestParam(required = false) Long companyId,
            Principal principal) {
        User admin = userService.getUserByUsername(principal.getName());
        List<SickLeave> sickLeaves;

        if (admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (companyId != null) {
                // SuperAdmin hat eine companyId im Request, also für diese Firma filtern
                sickLeaves = sickLeaveService.getAllSickLeavesInCompany(companyId, admin);
            } else {
                // SuperAdmin ohne companyId im Request sieht alle Krankmeldungen systemweit
                sickLeaves = sickLeaveService.getAllSickLeavesForSuperAdmin();
            }
        } else { // Regulärer Admin
            if (admin.getCompany() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN) // Admin ohne Firma
                        .body(List.of());
            }
            // Regulärer Admin sieht nur die seiner Firma, companyId Parameter wird ignoriert
            sickLeaves = sickLeaveService.getAllSickLeavesInCompany(admin.getCompany().getId(), admin);
        }
        return ResponseEntity.ok(sickLeaves);
    }

    // NEUER ENDPUNKT ZUM LÖSCHEN
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> deleteSickLeave(@PathVariable Long id, Principal principal) {
        try {
            sickLeaveService.deleteSickLeave(id, principal.getName());
            return ResponseEntity.ok(Map.of("message", "Krankmeldung erfolgreich gelöscht."));
        } catch (UserNotFoundException e) { // Falls Admin nicht gefunden wird
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) { // Falls Admin keine Berechtigung hat
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) { // Für andere Fehler, z.B. Krankmeldung nicht gefunden
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> updateSickLeave(@PathVariable Long id,
                                             @RequestBody SickLeaveUpdateRequest body,
                                             Principal principal) {
        try {
            SickLeave updated = sickLeaveService.updateSickLeave(
                    id,
                    principal.getName(),
                    body.getStartDate(),
                    body.getEndDate(),
                    Boolean.TRUE.equals(body.getHalfDay()),
                    body.getComment());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Fehler beim Aktualisieren der Krankmeldung: " + e.getMessage()));
        }
    }

    public static class SickLeaveUpdateRequest {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
        private Boolean halfDay;
        private String comment;

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDate endDate) {
            this.endDate = endDate;
        }

        public Boolean getHalfDay() {
            return halfDay;
        }

        public void setHalfDay(Boolean halfDay) {
            this.halfDay = halfDay;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}