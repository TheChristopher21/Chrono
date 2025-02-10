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
import java.time.LocalDate;
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

        CorrectionRequest req = new CorrectionRequest();
        req.setUser(user);
        req.setOriginalTimeTracking(original);
        req.setDesiredStartTime(desiredStart);
        req.setDesiredEndTime(desiredEnd);
        req.setReason(reason);
        req.setApproved(false);
        req.setDenied(false);

        if (workStartStr != null && !workStartStr.isEmpty()) {
            java.time.LocalTime workStart = java.time.LocalTime.parse(workStartStr);
            req.setWorkStart(workStart);
        }
        if (breakStartStr != null && !breakStartStr.isEmpty()) {
            java.time.LocalTime breakStart = java.time.LocalTime.parse(breakStartStr);
            req.setBreakStart(breakStart);
        }
        if (breakEndStr != null && !breakEndStr.isEmpty()) {
            java.time.LocalTime breakEnd = java.time.LocalTime.parse(breakEndStr);
            req.setBreakEnd(breakEnd);
        }
        if (workEndStr != null && !workEndStr.isEmpty()) {
            java.time.LocalTime workEnd = java.time.LocalTime.parse(workEndStr);
            req.setWorkEnd(workEnd);
        }

        System.out.println("Creating CorrectionRequest: " + req);
        return correctionRepo.save(req);
    }

    public List<CorrectionRequest> getOpenRequests() {
        return correctionRepo.findByApprovedFalseAndDeniedFalse();
    }

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

        // Ermittle das Datum der Correction anhand des desiredStartTime
        LocalDate correctionDay = req.getDesiredStartTime().toLocalDate();
        LocalDateTime dayStart = correctionDay.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        // Suche alle TimeTracking-Einträge für diesen Nutzer am betreffenden Tag
        List<TimeTracking> entries = timeRepo.findByUserAndStartTimeBetween(req.getUser(), dayStart, dayEnd);
        System.out.println("Found " + entries.size() + " time tracking entries for user "
                + req.getUser().getUsername() + " on " + correctionDay);

        // Falls noch keine Originalzeiten gesetzt sind, übernehme sie aus den Einträgen
        if (req.getOriginalWorkStart() == null || req.getOriginalBreakStart() == null ||
                req.getOriginalBreakEnd() == null || req.getOriginalWorkEnd() == null) {
            for (TimeTracking tt : entries) {
                int order = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
                switch (order) {
                    case 1:
                        if (req.getOriginalWorkStart() == null) {
                            req.setOriginalWorkStart(tt.getWorkStart());
                        }
                        break;
                    case 2:
                        if (req.getOriginalBreakStart() == null) {
                            req.setOriginalBreakStart(tt.getBreakStart());
                        }
                        break;
                    case 3:
                        if (req.getOriginalBreakEnd() == null) {
                            req.setOriginalBreakEnd(tt.getBreakEnd());
                        }
                        break;
                    case 4:
                        if (req.getOriginalWorkEnd() == null) {
                            req.setOriginalWorkEnd(tt.getWorkEnd());
                        }
                        break;
                    default:
                        System.out.println("Unexpected punchOrder: " + order);
                }
            }
            correctionRepo.save(req);
        }

        // Aktualisiere die TimeTracking-Einträge mit den neuen (korrigierten) Werten
        if (!entries.isEmpty()) {
            for (TimeTracking tt : entries) {
                int order = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
                if (order == 1) {
                    System.out.println("Updating Work Start for entry (punchOrder 1)");
                    tt.setStartTime(req.getDesiredStartTime());
                    tt.setWorkStart(req.getWorkStart());
                } else if (order == 2) {
                    System.out.println("Updating Break Start for entry (punchOrder 2)");
                    tt.setBreakStart(req.getBreakStart());
                    tt.setStartTime(LocalDateTime.of(tt.getStartTime().toLocalDate(), req.getBreakStart()));
                } else if (order == 3) {
                    System.out.println("Updating Break End for entry (punchOrder 3)");
                    tt.setBreakEnd(req.getBreakEnd());
                    tt.setStartTime(LocalDateTime.of(tt.getStartTime().toLocalDate(), req.getBreakEnd()));
                } else if (order == 4) {
                    System.out.println("Updating Work End for entry (punchOrder 4)");
                    tt.setEndTime(req.getDesiredEndTime());
                    tt.setWorkEnd(req.getWorkEnd());
                } else {
                    System.out.println("Unexpected punchOrder: " + order);
                }
                tt.setCorrected(true);
                timeRepo.save(tt);
                System.out.println("Updated entry with punchOrder " + order + ": " + tt);
            }
        } else {
            System.out.println("No existing time tracking entries found for correction, creating new ones.");
            // Erstelle neue Einträge
            TimeTracking tt1 = new TimeTracking();
            tt1.setUser(req.getUser());
            tt1.setPunchOrder(1);
            tt1.setStartTime(req.getDesiredStartTime());
            tt1.setWorkStart(req.getWorkStart());
            tt1.setCorrected(true);
            timeRepo.save(tt1);
            System.out.println("Created new entry: " + tt1);

            TimeTracking tt2 = new TimeTracking();
            tt2.setUser(req.getUser());
            tt2.setPunchOrder(2);
            tt2.setBreakStart(req.getBreakStart());
            tt2.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), req.getBreakStart()));
            tt2.setCorrected(true);
            timeRepo.save(tt2);
            System.out.println("Created new entry: " + tt2);

            TimeTracking tt3 = new TimeTracking();
            tt3.setUser(req.getUser());
            tt3.setPunchOrder(3);
            tt3.setBreakEnd(req.getBreakEnd());
            tt3.setStartTime(LocalDateTime.of(req.getDesiredStartTime().toLocalDate(), req.getBreakEnd()));
            tt3.setCorrected(true);
            timeRepo.save(tt3);
            System.out.println("Created new entry: " + tt3);

            TimeTracking tt4 = new TimeTracking();
            tt4.setUser(req.getUser());
            tt4.setPunchOrder(4);
            tt4.setEndTime(req.getDesiredEndTime());
            tt4.setWorkEnd(req.getWorkEnd());
            tt4.setCorrected(true);
            timeRepo.save(tt4);
            System.out.println("Created new entry: " + tt4);
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
