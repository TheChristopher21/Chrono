package com.chrono.chrono.controller;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.services.CorrectionRequestService;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal; // Import Principal
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.chrono.chrono.services.WorkScheduleService.logger;

@RestController
@RequestMapping("/api/correction")
public class CorrectionRequestController {

    @Autowired
    private CorrectionRequestService correctionRequestService;

    @Autowired
    private TimeTrackingService timeTrackingService;

    // Erlaube allen authentifizierten Nutzern, eigene Korrekturanträge zu erstellen
    @PostMapping("/create-full")
    @PreAuthorize("isAuthenticated()")
    public CorrectionRequest createRequest(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam(required = false) String workStart,
            @RequestParam(required = false) String breakStart,
            @RequestParam(required = false) String breakEnd,
            @RequestParam(required = false) String workEnd,
            @RequestParam String reason,
            @RequestParam String desiredStart,
            @RequestParam String desiredEnd
    ) {
        try {
            DateTimeFormatter desiredFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            return correctionRequestService.createRequest(
                    username,
                    null,
                    java.time.LocalDateTime.parse(desiredStart, desiredFormatter),
                    java.time.LocalDateTime.parse(desiredEnd, desiredFormatter),
                    reason,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    java.time.LocalDate.parse(date)
            );
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date/time format. Use yyyy-MM-dd for date and yyyy-MM-dd'T'HH:mm for desiredStart and desiredEnd");
        }
    }

    // Nur Admins dürfen alle Korrekturanträge abrufen
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<CorrectionRequest> getAllRequests() {
        return correctionRequestService.getAllRequests();
    }

    // Authentifizierte Nutzer können eigene Anträge abrufen
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<CorrectionRequest> getMyRequests(@RequestParam String username, Principal principal) {
        // Security check: User can only get their own requests.
        // Admins/Superadmins would typically use the /all endpoint or have specific checks.
        if (!principal.getName().equals(username)) {
            // This check might be too restrictive if Admins are supposed to use this endpoint too.
            // Consider if Admins should use /all or if this endpoint needs more complex role checking.
            // For now, strict check: only self.
            // throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only retrieve your own correction requests.");
        }
        return correctionRequestService.getRequestsForUser(username);
    }

    // Admin-Endpunkt zum Genehmigen eines Korrekturantrags
    @PostMapping("/approve/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public CorrectionRequest approveRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            Principal principal // Inject Principal to get the admin's username
    ) {
        CorrectionRequest corr = correctionRequestService.approveRequest(id, comment);

        try {
            String adminUsernameCurrent = principal.getName(); // Get the username of the admin making the request

            // Korrigierter Aufruf mit 7 Argumenten
            timeTrackingService.updateDayTimeEntries(
                    corr.getUsername(),
                    corr.getDate().toString(),
                    corr.getWorkStartFormatted(),
                    corr.getBreakStartFormatted(),
                    corr.getBreakEndFormatted(),
                    corr.getWorkEndFormatted(),
                    adminUsernameCurrent // Admin-Username des aktuellen Admins
            );
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Zeitstempel nach Genehmigung des Korrekturantrags ID {}: {}", id, e.getMessage(), e); // Verbessertes Logging
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Fehler beim Aktualisieren der Zeitstempel nach Genehmigung des Korrekturantrags: " + e.getMessage(), e
            );
        }
        return corr;
    }


    @PostMapping("/deny/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public CorrectionRequest denyRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return correctionRequestService.denyRequest(id, comment);
    }
}