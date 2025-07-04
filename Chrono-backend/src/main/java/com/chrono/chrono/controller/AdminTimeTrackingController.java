package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.dto.TimeTrackingImportRowDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {

    private static final Logger logger = LoggerFactory.getLogger(AdminTimeTrackingController.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/all-summaries")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAllTimeTrackSummaries(Principal principal) {
        User adminUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found: " + principal.getName()));
        Long adminCompanyId = (adminUser.getCompany() != null) ? adminUser.getCompany().getId() : null;
        boolean isSuperAdmin = adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (!isSuperAdmin && adminCompanyId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet.");
        }
        List<User> usersToList = isSuperAdmin ? userRepository.findAll() : userRepository.findByCompany_Id(adminCompanyId);
        List<DailyTimeSummaryDTO> result = usersToList.stream()
                .flatMap(u -> timeTrackingService.getUserHistory(u.getUsername()).stream())
                .sorted(Comparator.comparing(DailyTimeSummaryDTO::getUsername).thenComparing(DailyTimeSummaryDTO::getDate).reversed())
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> importTimestamps(@RequestParam("file") MultipartFile file, Principal principal) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null ||
            (!contentType.equals("application/vnd.ms-excel") &&
             !contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") &&
             !contentType.equals("application/CDFV2"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Ungültiger Dateityp oder leere Datei. Erhaltener Typ: " + contentType));
        }
        try {
            User currentUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found: " + principal.getName()));
            Long companyIdForImport = (currentUser.getCompany() != null) ? currentUser.getCompany().getId() : null;
            Map<String, Object> importResult = timeTrackingService.importTimeTrackingFromExcel(file.getInputStream(), companyIdForImport);
            
            List<?> errors = (List<?>) importResult.getOrDefault("errorMessages", Collections.emptyList());
            if (!errors.isEmpty()) {
                 importResult.put("message", "Import mit Fehlern abgeschlossen.");
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(importResult);
            }
            importResult.put("message", "Stempelzeiten erfolgreich importiert/aktualisiert.");
            return ResponseEntity.ok(importResult);
        } catch (Exception e) {
            logger.error("Fehler beim Import der Stempelzeiten: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Fehler beim Import: " + e.getMessage()));
        }
    }

    @PostMapping("/import/json")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> importTimestampsJson(@RequestBody List<TimeTrackingImportRowDTO> rows, Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found: " + principal.getName()));
            Long companyIdForImport = (currentUser.getCompany() != null) ? currentUser.getCompany().getId() : null;
            Map<String, Object> importResult = timeTrackingService.importTimeTrackingFromRows(rows, companyIdForImport);

            List<?> errors = (List<?>) importResult.getOrDefault("errorMessages", Collections.emptyList());
            if (!errors.isEmpty()) {
                importResult.put("message", "Import mit Fehlern abgeschlossen.");
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(importResult);
            }
            importResult.put("message", "Stempelzeiten erfolgreich importiert/aktualisiert.");
            return ResponseEntity.ok(importResult);
        } catch (Exception e) {
            logger.error("Fehler beim JSON-Import der Stempelzeiten: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Fehler beim Import: " + e.getMessage()));
        }
    }

    @PostMapping("/rebuild-balances")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<String> rebuildBalances() {
        timeTrackingService.rebuildAllUserBalancesOnce();
        return ResponseEntity.accepted().body("Balance-Rebuild erfolgreich angestoßen.");
    }

    @GetMapping("/admin/weekly-balance")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAdminWeeklyBalances(@RequestParam String monday, Principal principal) {
        LocalDate mondayDate = LocalDate.parse(monday);
        User adminUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        List<User> usersToList = adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ?
                                 userRepository.findAll() : 
                                 (adminUser.getCompany() != null ? userRepository.findByCompany_Id(adminUser.getCompany().getId()) : Collections.emptyList());
        if (usersToList.isEmpty() && !adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet oder Firma hat keine User.");
        }
        List<Map<String, Object>> result = usersToList.stream().map(u -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("weeklyBalance", timeTrackingService.getWeeklyBalance(u, mondayDate));
            entry.put("color", u.getColor());
            return entry;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/tracking-balances")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAllCurrentTrackingBalances(Principal principal) {
        User adminUser = userRepository.findByUsername(principal.getName()).orElseThrow();
         List<User> usersToList = adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ?
                                 userRepository.findAll() : 
                                 (adminUser.getCompany() != null ? userRepository.findByCompany_Id(adminUser.getCompany().getId()) : Collections.emptyList());
        if (usersToList.isEmpty() && !adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet oder Firma hat keine User.");
        }
        List<Map<String, Object>> result = usersToList.stream().map(u -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("trackingBalance", u.getTrackingBalanceInMinutes());
            entry.put("color", u.getColor());
            return entry;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/editDay/{targetUsername}/{date}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> editDayTimeEntries(
            @PathVariable String targetUsername,
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody List<TimeTrackingEntryDTO> updatedEntries, Principal principal) {
        try {
            String adminUsername = principal.getName();
            User adminUser = userRepository.findByUsername(adminUsername).orElseThrow();
            User targetUser = userRepository.findByUsername(targetUsername).orElseThrow(() -> new RuntimeException("Zielbenutzer nicht gefunden."));

            if (!adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) &&
                (adminUser.getCompany() == null || targetUser.getCompany() == null || !adminUser.getCompany().getId().equals(targetUser.getCompany().getId()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin kann keine Benutzer anderer Firmen bearbeiten."));
            }
            String result = timeTrackingService.updateDayTimeEntries(targetUsername, date.toString(), updatedEntries, adminUsername);
            return ResponseEntity.ok(Map.of("message", result));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in editDayTimeEntries for User {} on {}: {}", targetUsername, date, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            logger.error("RuntimeException in editDayTimeEntries for User {} on {}: {}", targetUsername, date, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Fehler beim Bearbeiten der Zeiteinträge: " + ex.getMessage()));
        }
    }
}
