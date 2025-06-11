package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class CorrectionRequestService {

    private static final Logger logger = LoggerFactory.getLogger(CorrectionRequestService.class);

    @Autowired
    private CorrectionRequestRepository correctionRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepo;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @Transactional
    public CorrectionRequest createCorrectionRequest(String username, Long targetEntryId,
                                           LocalDateTime desiredTimestamp, String desiredPunchTypeStr,
                                           String reason, LocalDate requestDate) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User '" + username + "' not found"));

        TimeTrackingEntry targetEntry = null;
        if (targetEntryId != null) {
            targetEntry = timeTrackingEntryRepo.findById(targetEntryId).orElse(null);
        }

        TimeTrackingEntry.PunchType desiredPunchType;
        try {
            desiredPunchType = TimeTrackingEntry.PunchType.valueOf(desiredPunchTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungültiger desiredPunchType: " + desiredPunchTypeStr);
        }

        CorrectionRequest req;
        if (targetEntry != null) {
            req = new CorrectionRequest(user, requestDate, targetEntry, desiredTimestamp, reason);
            req.setDesiredPunchType(desiredPunchType);
        } else {
            req = new CorrectionRequest(user, requestDate, desiredTimestamp, desiredPunchType, reason);
        }

        logger.info("Creating CorrectionRequest for user {}, targetEntryId {}, desiredTime {}, desiredType {}, reason {}, requestDate {}",
                username, targetEntryId, desiredTimestamp, desiredPunchType, reason, requestDate);
        return correctionRepo.save(req);
    }

    public List<CorrectionRequest> getOpenRequests() {
        return correctionRepo.findByApprovedFalseAndDeniedFalse();
    }

    public List<CorrectionRequest> getAllRequestsForPrincipal(Principal principal) {
        User requestingUser = userRepo.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Anfragender Benutzer nicht gefunden: " + principal.getName()));

        boolean isSuperAdmin = requestingUser.getRoles().stream()
                .anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN"));

        // Ein SUPERADMIN sieht alles
        if (isSuperAdmin) {
            return correctionRepo.findAll();
        }

        boolean isAdmin = requestingUser.getRoles().stream()
                .anyMatch(role -> role.getRoleName().equals("ROLE_ADMIN"));

        // Ein normaler ADMIN sieht nur Anträge aus der eigenen Firma
        if (isAdmin) {
            if (requestingUser.getCompany() == null) {
                // Admin ohne Firma kann keine firmenbezogenen Anträge sehen.
                return Collections.emptyList();
            }
            Long companyId = requestingUser.getCompany().getId();
            return correctionRepo.findAllByCompanyId(companyId); // Hier wird die neue Repository-Methode verwendet
        }

        // Benutzer ohne Admin-Rolle sollten hier gar nicht erst hinkommen (dank @PreAuthorize),
        // aber zur Sicherheit eine leere Liste zurückgeben.
        return Collections.emptyList();
    }

    public List<CorrectionRequest> getRequestsForUser(String username) {
        // Die Logik zum Finden des Benutzers bleibt gleich.
        userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        // RUFEN SIE STATTDESSEN DIE NEUE REPOSITORY-METHODE AUF
        return correctionRepo.findByUserWithDetails(username);
    }
    @Transactional
    public CorrectionRequest approveRequest(Long requestId, String comment, String adminUsername) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }

        User targetUser = req.getUser();
        LocalDateTime desiredTimestamp = req.getDesiredTimestamp();
        TimeTrackingEntry.PunchType desiredPunchType = req.getDesiredPunchType();

        if (targetUser == null || desiredTimestamp == null || desiredPunchType == null) {
            throw new IllegalStateException("CorrectionRequest ID " + requestId + " hat ungültige Daten.");
        }

        TimeTrackingEntry entryToModify = req.getTargetEntry();

        if (entryToModify != null) {
            if (!entryToModify.getUser().getId().equals(targetUser.getId())) {
                logger.error("Security violation: CorrectionRequest ID {} attempts to modify entry ID {} of another user ({}) than applicant ({}).",
                    requestId, entryToModify.getId(), entryToModify.getUser().getUsername(), targetUser.getUsername());
                throw new SecurityException("Correction request and target entry do not belong to the same user.");
            }
            entryToModify.setEntryTimestamp(desiredTimestamp);
            entryToModify.setPunchType(desiredPunchType);
            entryToModify.setSource(TimeTrackingEntry.PunchSource.USER_CORRECTION);
            entryToModify.setCorrectedByUser(true);
            entryToModify.setSystemGeneratedNote(null);
            timeTrackingEntryRepo.save(entryToModify);
            logger.info("Admin {}: Approved CorrectionRequest ID {}. Updated TimeTrackingEntry ID {} for User {} to: {} {}",
                adminUsername, requestId, entryToModify.getId(), targetUser.getUsername(), desiredTimestamp, desiredPunchType);
        } else {
            TimeTrackingEntry newEntry = new TimeTrackingEntry(targetUser, desiredTimestamp, desiredPunchType, TimeTrackingEntry.PunchSource.USER_CORRECTION);
            newEntry.setCorrectedByUser(true);
            timeTrackingEntryRepo.save(newEntry);
            logger.info("Admin {}: Approved CorrectionRequest ID {}. Created new TimeTrackingEntry for User {} with: {} {}",
                adminUsername, requestId, targetUser.getUsername(), desiredTimestamp, desiredPunchType);
        }

        req.setApproved(true);
        req.setDenied(false);
        req.setAdminComment(comment);
        CorrectionRequest savedReq = correctionRepo.save(req);
        timeTrackingService.rebuildUserBalance(targetUser);
        return savedReq;
    }

    // Fügen Sie diese ZWEI Methoden komplett neu in die Service-Klasse ein:

    public List<CorrectionRequest> getAllRequests() {
        // Lädt alle Anfragen, ideal für Super-Admins.
        return correctionRepo.findAll();
    }

    public List<CorrectionRequest> getRequestsByCompany(Long companyId) {
        // Lädt alle Anfragen für eine spezifische Company-ID.
        return correctionRepo.findAllByCompanyId(companyId);
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
        req.setAdminComment(comment);
        return correctionRepo.save(req);
    }

    public UserRepository getUserRepo() { return userRepo; }
}
