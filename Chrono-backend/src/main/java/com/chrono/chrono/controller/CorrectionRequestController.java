package com.chrono.chrono.controller;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.CorrectionRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/correction")
public class CorrectionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(CorrectionRequestController.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CorrectionRequestService correctionRequestService;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createCorrectionRequest(
            @RequestParam String username,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate requestDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime desiredTimestamp,
            @RequestParam String desiredPunchType,
            @RequestParam String reason,
            @RequestParam(required = false) Long targetEntryId,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
             var requestingUser = correctionRequestService.getUserRepo().findByUsername(principal.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anfragender Benutzer nicht gefunden."));
            var targetUser = correctionRequestService.getUserRepo().findByUsername(username)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Zielbenutzer nicht gefunden."));

            boolean isSuperAdmin = requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
            boolean isAdminOfSameCompany = false;
            if (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                if (requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                    requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                    isAdminOfSameCompany = true;
                }
            }
            if (!isSuperAdmin && !isAdminOfSameCompany) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Sie dürfen keine Korrekturanträge für andere Benutzer erstellen."));
            }
        }

        try {
            CorrectionRequest createdRequest = correctionRequestService.createCorrectionRequest(
                    username,
                    targetEntryId,
                    desiredTimestamp,
                    desiredPunchType,
                    reason,
                    requestDate
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(createdRequest);
        } catch (DateTimeParseException e) {
            logger.warn("Fehler beim Parsen des Datums/Zeit für Korrekturantrag von {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", "Ungültiges Datums- oder Zeitformat. Erwartet: yyyy-MM-dd für requestDate, yyyy-MM-dd'T'HH:mm:ss für desiredTimestamp."));
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültiges Argument beim Erstellen des Korrekturantrags für {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Fehler beim Erstellen des Korrekturantrags für {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Interner Fehler beim Erstellen des Antrags."));
        }
    }


    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<List<CorrectionRequest>> getAllPendingRequests(Principal principal) {
        try {
            User currentUser = userRepo.findByUsername(principal.getName())
                    .orElseThrow(() -> new SecurityException("Authentifizierter Benutzer nicht in der Datenbank gefunden."));

            List<CorrectionRequest> requests;

            // KORREKTUR: Verwendet jetzt die korrekte Methode .getRoles()
            boolean isSuperAdmin = currentUser.getRoles().stream()
                    .anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));

            if (isSuperAdmin) {
                requests = correctionRequestService.getAllRequests();
            } else {
                if (currentUser.getCompany() == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Collections.emptyList());
                }
                requests = correctionRequestService.getRequestsByCompany(currentUser.getCompany().getId());
            }

            return ResponseEntity.ok(requests);

        } catch (Exception e) {
            logger.error("Fehler beim Abrufen aller Korrekturanträge durch {}: {}", principal.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CorrectionRequest>> getMyRequests(Principal principal) {
        return ResponseEntity.ok(correctionRequestService.getRequestsForUser(principal.getName()));
    }

    // Add this new method inside the CorrectionRequestController class

    @GetMapping("/user/{username}")
    @PreAuthorize("#username == principal.name or hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<List<CorrectionRequest>> getRequestsForUser(@PathVariable String username) {
        return ResponseEntity.ok(correctionRequestService.getRequestsForUser(username));
    }

    @PostMapping("/approve/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> approveRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            Principal principal
    ) {
        try {
            CorrectionRequest approvedRequest = correctionRequestService.approveRequest(id, comment, principal.getName());
            return ResponseEntity.ok(approvedRequest);
        } catch (SecurityException e) {
            logger.warn("Sicherheitsverletzung beim Genehmigen des Korrekturantrags ID {} durch {}: {}", id, principal.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Fehler beim Genehmigen des Korrekturantrags ID {} durch {}: {}", id, principal.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }


    @PostMapping("/deny/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> denyRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            Principal principal
    ) {
         try {
            CorrectionRequest deniedRequest = correctionRequestService.denyRequest(id, comment);
            return ResponseEntity.ok(deniedRequest);
        } catch (SecurityException e) {
            logger.warn("Sicherheitsverletzung beim Ablehnen des Korrekturantrags ID {} durch {}: {}", id, principal.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Fehler beim Ablehnen des Korrekturantrags ID {} durch {}: {}", id, principal.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}
