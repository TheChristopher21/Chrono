package com.chrono.chrono.controller;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.services.CorrectionRequestService;
import com.chrono.chrono.services.TimeTrackingService;
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

    @Autowired
    private TimeTrackingService timeTrackingService;

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

    /**
     * Beim Approve eines Korrekturantrags wird der Antrag als approved markiert
     * und anschließend werden die neuen Zeiten in der Zeiterfassung (TimeTracking) übernommen.
     * Da kein Admin-Passwort mehr benötigt wird, erfolgt der Aufruf ohne Passwort.
     */
    @PostMapping("/approve/{id}")
    public CorrectionRequest approveRequest(@PathVariable Long id) {
        CorrectionRequest corr = correctionRequestService.approveRequest(id);
        try {
            timeTrackingService.updateDayTimeEntries(
                    corr.getUsername(),
                    corr.getDate().toString(),
                    corr.getWorkStartFormatted(),
                    corr.getBreakStartFormatted(),
                    corr.getBreakEndFormatted(),
                    corr.getWorkEndFormatted(),
                    null, // Kein Admin-Username
                    null, // Kein Admin-Passwort
                    corr.getUserPassword() // Falls vorhanden; sonst kann auch null übergeben werden
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Fehler beim Aktualisieren der Zeitstempel: " + e.getMessage());
        }
        return corr;
    }

    @PostMapping("/deny/{id}")
    public CorrectionRequest denyRequest(@PathVariable Long id) {
        return correctionRequestService.denyRequest(id);
    }
}
