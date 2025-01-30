package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CorrectionRequestService {

    @Autowired
    private CorrectionRequestRepository correctionRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TimeTrackingRepository timeRepo;

    /**
     * Erstellt eine neue Korrekturanfrage für einen Benutzer.
     */
    @Transactional
    public CorrectionRequest createRequest(String username, Long timeTrackingId, LocalDateTime desiredStart, LocalDateTime desiredEnd, String reason) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User '" + username + "' not found"));

        TimeTracking original = null;
        if (timeTrackingId != null) {
            original = timeRepo.findById(timeTrackingId)
                    .orElseThrow(() -> new RuntimeException("TimeTracking entry with ID " + timeTrackingId + " not found"));
        }

        CorrectionRequest req = new CorrectionRequest();
        req.setUser(user);
        req.setOriginalTimeTracking(original);
        req.setDesiredStartTime(desiredStart);
        req.setDesiredEndTime(desiredEnd);
        req.setReason(reason);
        req.setApproved(false);
        req.setDenied(false);

        return correctionRepo.save(req);
    }

    /**
     * Gibt alle offenen Korrekturanfragen zurück und lädt relevante Daten mit.
     */
    public List<CorrectionRequest> getOpenRequests() {
        List<CorrectionRequest> requests = correctionRepo.findAll();
        for (CorrectionRequest req : requests) {
            System.out.println("DEBUG API: ID=" + req.getId() +
                    ", OrigStart=" + (req.getOriginalTimeTracking() != null ? req.getOriginalTimeTracking().getStartTime() : "NULL") +
                    ", OrigEnd=" + (req.getOriginalTimeTracking() != null ? req.getOriginalTimeTracking().getEndTime() : "NULL"));
        }
        return requests;
    }

    /**
     * Genehmigt eine Korrekturanfrage, falls der Benutzer ein Manager oder Admin ist.
     */
    @Transactional
    public CorrectionRequest approveRequest(Long requestId, String adminPassword) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }

        req.setApproved(true);
        req.setDenied(false);
        CorrectionRequest saved = correctionRepo.save(req);

        // Falls eine Korrektur eines bestehenden TimeTracking-Eintrags vorliegt
        if (req.getOriginalTimeTracking() != null) {
            TimeTracking tt = req.getOriginalTimeTracking();
            tt.setStartTime(req.getDesiredStartTime());
            tt.setEndTime(req.getDesiredEndTime());
            tt.setCorrected(true);
            timeRepo.save(tt);
        } else {
            // Falls kein existierender TimeTracking-Eintrag existiert, kann ein neuer erstellt werden.
            TimeTracking newTracking = new TimeTracking();
            newTracking.setUser(req.getUser());
            newTracking.setStartTime(req.getDesiredStartTime());
            newTracking.setEndTime(req.getDesiredEndTime());
            newTracking.setCorrected(true);
            timeRepo.save(newTracking);
        }

        return saved;
    }

    /**
     * Lehnen eine Korrekturanfrage ab.
     */
    @Transactional
    public CorrectionRequest denyRequest(Long requestId) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }

        req.setDenied(true);
        req.setApproved(false);
        return correctionRepo.save(req);
    }
}
