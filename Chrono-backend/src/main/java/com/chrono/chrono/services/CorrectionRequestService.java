package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class CorrectionRequestService {

    @Autowired
    private CorrectionRequestRepository correctionRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TimeTrackingRepository timeRepo;

    @Transactional
    public CorrectionRequest createRequest(String username, Long timeTrackingId,
                                           LocalDateTime desiredStart, LocalDateTime desiredEnd,
                                           String reason,
                                           String workStartStr, String breakStartStr,
                                           String breakEndStr, String workEndStr,
                                           LocalDate date) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User '" + username + "' not found"));

        TimeTracking original = null;
        if (timeTrackingId != null) {
            original = timeRepo.findById(timeTrackingId)
                    .orElseThrow(() -> new RuntimeException("TimeTracking entry with ID " + timeTrackingId + " not found"));
        }

        CorrectionRequest req = new CorrectionRequest(user, original, desiredStart, desiredEnd, reason);

        if (workStartStr != null && !workStartStr.isEmpty()) {
            req.setWorkStart(java.time.LocalTime.parse(workStartStr));
        }
        if (breakStartStr != null && !breakStartStr.isEmpty()) {
            req.setBreakStart(java.time.LocalTime.parse(breakStartStr));
        }
        if (breakEndStr != null && !breakEndStr.isEmpty()) {
            req.setBreakEnd(java.time.LocalTime.parse(breakEndStr));
        }
        if (workEndStr != null && !workEndStr.isEmpty()) {
            req.setWorkEnd(java.time.LocalTime.parse(workEndStr));
        }

        System.out.println("DEBUG: Creating CorrectionRequest: " + req);
        return correctionRepo.save(req);
    }

    public List<CorrectionRequest> getOpenRequests() {
        return correctionRepo.findByApprovedFalseAndDeniedFalse();
    }

    public List<CorrectionRequest> getAllRequests() {
        return correctionRepo.findAllWithOriginalTimes();
    }

    // Neue Methode: Alle Korrekturanträge eines Users abrufen
    public List<CorrectionRequest> getRequestsForUser(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User '" + username + "' not found"));
        return correctionRepo.findByUser(user);
    }

    /**
     * Genehmigt den Korrekturantrag, ohne Admin-Passwort zu verlangen.
     * Anschließend werden die Zeiteinträge für den entsprechenden Tag überschrieben.
     */
    @Transactional
    public CorrectionRequest approveRequest(Long requestId, String comment) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }

        // 1) Nur den Antrag als akzeptiert markieren + Admin-Kommentar setzen
        req.setApproved(true);
        req.setDenied(false);
        req.setAdminComment(comment);

        // 2) NICHT mehr selbst WorkStart/BreakStart/... anlegen!
        //    Wir lassen das 'updateDayTimeEntries' aus dem TimeTrackingService machen.

        // => Speichern, fertig.
        return correctionRepo.save(req);
    }


    private CorrectionRequest loadAndValidate(Long id) {
        CorrectionRequest req = correctionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + id + " not found"));
        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request already processed.");
        }
        return req;
    }

    @Transactional
    public CorrectionRequest denyRequest(Long requestId, String comment) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));
        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }
        req.setDenied(true);
        req.setApproved(false);
        req.setAdminComment(comment);          // ⬅️  speichern
        return correctionRepo.save(req);
    }
}
