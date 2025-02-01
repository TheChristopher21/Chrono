package com.chrono.chrono.controller;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.services.CorrectionRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/correction")
public class CorrectionRequestController {

    @Autowired
    private CorrectionRequestService correctionRequestService;

    /**
     * Erstellt eine neue Korrekturanfrage.
     */
    @PostMapping("/create")
    public CorrectionRequest createRequest(
            @RequestParam String username,
            @RequestParam(required = false) Long timeTrackingId,
            @RequestParam String desiredStart,
            @RequestParam String desiredEnd,
            @RequestParam String reason
    ) {
        try {
            LocalDateTime start = LocalDateTime.parse(desiredStart);
            LocalDateTime end = LocalDateTime.parse(desiredEnd);

            return correctionRequestService.createRequest(username, timeTrackingId, start, end, reason);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use 'yyyy-MM-ddTHH:mm:ss'");
        }
    }


    /**
     * Liefert alle offenen Korrekturanfragen.
     */
    @GetMapping("/open")
    public List<CorrectionRequest> getAllOpenRequests() {
        List<CorrectionRequest> requests = correctionRequestService.getOpenRequests();

        for (CorrectionRequest req : requests) {
            System.out.println("DEBUG API: ID=" + req.getId() + ", OrigStart=" + req.getOriginalStartTime() + ", OrigEnd=" + req.getOriginalEndTime());
            System.out.println("API Response: " + requests);
        }

        return requests;
    }


    /**
     * Genehmigt eine Korrekturanfrage.
     */
    @PostMapping("/approve/{id}")
    public CorrectionRequest approveRequest(
            @PathVariable Long id,
            @RequestParam String adminPassword
    ) {
        return correctionRequestService.approveRequest(id, adminPassword);
    }

    /**
     * Lehnt eine Korrekturanfrage ab.
     */
    @PostMapping("/deny/{id}")
    public CorrectionRequest denyRequest(
            @PathVariable Long id
    ) {
        return correctionRequestService.denyRequest(id);
    }
}
