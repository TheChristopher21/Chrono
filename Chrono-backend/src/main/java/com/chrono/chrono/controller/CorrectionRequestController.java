package com.chrono.chrono.controller;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.services.CorrectionRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/correction")
public class CorrectionRequestController {

    @Autowired
    private CorrectionRequestService correctionRequestService;

    @PostMapping("/create-full")
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

    @GetMapping("/open")
    public List<CorrectionRequest> getAllOpenRequests() {
        return correctionRequestService.getOpenRequests();
    }

    @GetMapping("/all")
    public List<CorrectionRequest> getAllRequests() {
        return correctionRequestService.getAllRequests();
    }

    // Neuer Endpunkt: Alle Korrekturanträge des angemeldeten Users abrufen
    @GetMapping("/my")
    public List<CorrectionRequest> getMyRequests(@RequestParam String username) {
        return correctionRequestService.getRequestsForUser(username);
    }

    @PostMapping("/approve/{id}")
    public CorrectionRequest approveRequest(
            @PathVariable Long id,
            @RequestParam String adminPassword
    ) {
        return correctionRequestService.approveRequest(id, adminPassword);
    }

    @PostMapping("/deny/{id}")
    public CorrectionRequest denyRequest(
            @PathVariable Long id
    ) {
        return correctionRequestService.denyRequest(id);
    }
}
