package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAllTimeTracks(Principal principal) {
        try {
            User adminUser = userRepository
                    .findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found: " + principal.getName()));

            if (adminUser.getCompany() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet.");
            }
            Long adminCompanyId = adminUser.getCompany().getId();

            List<User> allUsersInCompany = userRepository.findByCompany_Id(adminCompanyId);
            List<AdminTimeTrackDTO> result = new ArrayList<>();

            for (User u : allUsersInCompany) {
                List<TimeTracking> rows = timeTrackingService.getTimeTrackingRowsForUser(u);
                for (TimeTracking row : rows) {
                    result.add(new AdminTimeTrackDTO(row));
                }
            }
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> importTimestamps(@RequestParam("file") MultipartFile file, Principal principal) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Bitte eine Datei zum Hochladen auswählen."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/vnd.ms-excel") &&
                !contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") &&
                !contentType.equals("application/CDFV2")
        )) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Ungültiger Dateityp. Bitte eine Excel-Datei hochladen (.xls oder .xlsx). Erhaltener Typ: " + contentType));
        }

        try {
            User currentUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found: " + principal.getName()));

            if (currentUser.getCompany() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Admin ist keiner Firma zugeordnet."));
            }
            Long companyId = currentUser.getCompany().getId();

            Map<String, Object> importResult = timeTrackingService.importTimeTrackingFromExcel(file.getInputStream(), companyId);

            List<String> errors = (List<String>) importResult.getOrDefault("errorMessages", Collections.emptyList());
            int importedCount = (int) importResult.getOrDefault("importedCount", 0);

            if (!errors.isEmpty()) {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("message", "Import mit Fehlern abgeschlossen.");
                responseBody.put("importedCount", importedCount);
                responseBody.put("errors", errors);
                responseBody.put("successMessages", importResult.getOrDefault("successMessages", Collections.emptyList()));
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(responseBody);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Stempelzeiten erfolgreich importiert.",
                    "importedCount", importedCount,
                    "successMessages", importResult.getOrDefault("successMessages", Collections.emptyList())
            ));

        } catch (Exception e) {
            System.err.println("Fehler beim Import der Stempelzeiten: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","Fehler beim Import der Stempelzeiten: " + e.getMessage()));
        }
    }


    @PostMapping("/rebuild-balances")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<String> rebuildBalances() {
        timeTrackingService.rebuildAllUserBalancesOnce();
        return ResponseEntity.accepted()
                .body("Balance-Rebuild erfolgreich angestoßen.");
    }

    @GetMapping("/admin/weekly-balance")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAdminWeeklyBalances(@RequestParam String monday, Principal principal) {
        LocalDate mondayDate = LocalDate.parse(monday);

        User adminUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + principal.getName()));
        if (adminUser.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet.");
        }
        Long companyId = adminUser.getCompany().getId();
        List<User> usersInCompany = userRepository.findByCompany_Id(companyId);


        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : usersInCompany) {
            int weekly = timeTrackingService.getWeeklyBalance(u, mondayDate);
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("weeklyBalance", weekly);
            entry.put("color", u.getColor());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/tracking-balances")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAllCurrentTrackingBalances(Principal principal) {
        User adminUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + principal.getName()));
        if (adminUser.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ist keiner Firma zugeordnet.");
        }
        Long companyId = adminUser.getCompany().getId();
        List<User> usersInCompany = userRepository.findByCompany_Id(companyId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : usersInCompany) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("trackingBalance", u.getTrackingBalanceInMinutes());
            entry.put("color", u.getColor());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/editDay")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> editDayTimeEntries(
            @RequestParam String targetUsername,
            @RequestParam String date,
            @RequestParam(required = false) String workStart, // Make parameters optional if they can be null/empty
            @RequestParam(required = false) String breakStart,
            @RequestParam(required = false) String breakEnd,
            @RequestParam(required = false) String workEnd,
            @RequestParam String adminUsername, // This is the username of the admin performing the action
            Principal principal
    ) {
        try {
            // Ensure the admin performing the action is the one logged in
            if (!principal.getName().equals(adminUsername)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Aktion nicht erlaubt. Admin-Username stimmt nicht mit angemeldetem User überein.");
            }

            // Check if targetUser belongs to the admin's company
            User adminPerformingAction = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin " + adminUsername + " nicht gefunden."));
            User targetUserEntity = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new RuntimeException("Zielbenutzer " + targetUsername + " nicht gefunden."));

            if (adminPerformingAction.getCompany() == null || targetUserEntity.getCompany() == null ||
                    !adminPerformingAction.getCompany().getId().equals(targetUserEntity.getCompany().getId())) {
                // Allow SUPERADMIN to edit users from any company
                if (!adminPerformingAction.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin kann keine Benutzer anderer Firmen bearbeiten.");
                }
            }

            // Call the service method - ensure its signature is also updated
            String result = timeTrackingService.updateDayTimeEntries(
                    targetUsername, date,
                    workStart, breakStart, breakEnd, workEnd,
                    adminUsername // Pass adminUsername for audit/logging or other checks if needed
                    // The actual password check is removed.
            );
            return ResponseEntity.ok(Map.of("message", result)); // Return a JSON object

        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            // Removed password-specific error checks as passwords are no longer passed/checked here
            if (msg.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", msg));
            }
            // Log the detailed error for internal review
            System.err.println("Error editing dayTimeEntries: " + msg);
            ex.printStackTrace(); // For debugging purposes

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Konnte Zeiteinträge nicht bearbeiten: " + msg));
        }  catch (Exception e) {
            // Catch any other unexpected exceptions
            System.err.println("Unexpected error in editDayTimeEntries: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ein interner Serverfehler ist aufgetreten."));
        }
    }
}