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
    public CorrectionRequest approveRequest(Long requestId) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }

        // Markiere den Request als genehmigt
        req.setApproved(true);
        req.setDenied(false);
        CorrectionRequest saved = correctionRepo.save(req);

        // Aktualisiere die TimeTracking-Einträge
        LocalDate correctionDay = req.getDesiredStartTime().toLocalDate();
        LocalDateTime dayStart = correctionDay.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<TimeTracking> entries = timeRepo.findByUserAndStartTimeBetween(req.getUser(), dayStart, dayEnd);
        System.out.println("DEBUG: Found " + entries.size() + " time tracking entries for user "
                + req.getUser().getUsername() + " on " + correctionDay);

        // Falls Originalzeiten nicht gesetzt sind, fülle sie ggf. aus bestehenden Einträgen
        if (req.getOriginalWorkStart() == null || req.getOriginalBreakStart() == null ||
                req.getOriginalBreakEnd() == null || req.getOriginalWorkEnd() == null) {
            for (TimeTracking tt : entries) {
                int order = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
                switch (order) {
                    case 1:
                        if (req.getOriginalWorkStart() == null) req.setOriginalWorkStart(tt.getWorkStart());
                        break;
                    case 2:
                        if (req.getOriginalBreakStart() == null) req.setOriginalBreakStart(tt.getBreakStart());
                        break;
                    case 3:
                        if (req.getOriginalBreakEnd() == null) req.setOriginalBreakEnd(tt.getBreakEnd());
                        break;
                    case 4:
                        if (req.getOriginalWorkEnd() == null) req.setOriginalWorkEnd(tt.getWorkEnd());
                        break;
                    default:
                        System.out.println("DEBUG: Unexpected punchOrder: " + order);
                }
            }
            correctionRepo.save(req);
        }

        if (!entries.isEmpty()) {
            for (TimeTracking tt : entries) {
                int order = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
                switch (order) {
                    case 1:
                        tt.setStartTime(req.getDesiredStartTime());
                        tt.setWorkStart(req.getWorkStart());
                        break;
                    case 2:
                        LocalTime breakStart = req.getBreakStart() != null ? req.getBreakStart() : req.getDesiredStartTime().toLocalTime();
                        tt.setBreakStart(breakStart);
                        tt.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), breakStart));
                        break;
                    case 3:
                        LocalTime breakEnd = req.getBreakEnd() != null ? req.getBreakEnd() : req.getDesiredStartTime().toLocalTime();
                        tt.setBreakEnd(breakEnd);
                        tt.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), breakEnd));
                        break;
                    case 4:
                        tt.setEndTime(req.getDesiredEndTime());
                        tt.setWorkEnd(req.getWorkEnd());
                        break;
                    default:
                        System.out.println("DEBUG: Unexpected punchOrder: " + order);
                }
                tt.setCorrected(true);
                timeRepo.save(tt);
            }
        } else {
            // Falls keine Einträge vorhanden sind, neue erstellen
            System.out.println("DEBUG: No existing time tracking entries found for correction, creating new ones.");

            TimeTracking tt1 = new TimeTracking();
            tt1.setUser(req.getUser());
            tt1.setPunchOrder(1);
            tt1.setStartTime(req.getDesiredStartTime());
            tt1.setWorkStart(req.getWorkStart());
            tt1.setCorrected(true);
            timeRepo.save(tt1);

            // PunchOrder 2 – Break Start
            TimeTracking tt2 = new TimeTracking();
            tt2.setUser(req.getUser());
            tt2.setPunchOrder(2);
            LocalTime breakStart = req.getBreakStart() != null ? req.getBreakStart() : req.getDesiredStartTime().toLocalTime();
            tt2.setBreakStart(breakStart);
            tt2.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), breakStart));
            tt2.setCorrected(true);
            timeRepo.save(tt2);

            // PunchOrder 3 – Break End
            TimeTracking tt3 = new TimeTracking();
            tt3.setUser(req.getUser());
            tt3.setPunchOrder(3);
            LocalTime breakEnd = req.getBreakEnd() != null ? req.getBreakEnd() : req.getDesiredStartTime().toLocalTime();
            tt3.setBreakEnd(breakEnd);
            tt3.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), breakEnd));
            tt3.setCorrected(true);
            timeRepo.save(tt3);

            // PunchOrder 4 – Work End
            TimeTracking tt4 = new TimeTracking();
            tt4.setUser(req.getUser());
            tt4.setPunchOrder(4);
            tt4.setStartTime(req.getDesiredEndTime());
            tt4.setEndTime(req.getDesiredEndTime());
            tt4.setWorkEnd(req.getWorkEnd());
            tt4.setCorrected(true);
            timeRepo.save(tt4);
        }

        return saved;
    }


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
