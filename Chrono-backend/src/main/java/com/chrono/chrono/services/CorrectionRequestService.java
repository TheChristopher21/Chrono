package com.chrono.chrono.services;

import com.chrono.chrono.security.CompanyScope;
import com.chrono.chrono.dto.AdminCorrectionRequestDTO;
import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.dto.CorrectionOriginalEntry;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.utils.UserInitials;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    @Autowired
    private AccessControlService accessControlService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public CorrectionRequest createCorrectionRequest(String username, Long targetEntryId,
                                           LocalDateTime desiredTimestamp, String desiredPunchTypeStr,
                                           String reason, LocalDate requestDate) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User '" + username + "' not found"));
        CompanyScope.requireTarget(user);

        TimeTrackingEntry targetEntry = null;
        if (targetEntryId != null) {
            targetEntry = timeTrackingEntryRepo.findById(targetEntryId)
                    .orElseThrow(() -> new IllegalArgumentException("Der ursprüngliche Stempel existiert nicht mehr. Bitte den Tag neu laden."));
            if (targetEntry.getUser() == null || !user.getId().equals(targetEntry.getUser().getId())
                    || !desiredTimestamp.toLocalDate().equals(targetEntry.getEntryTimestamp().toLocalDate())) {
                throw new IllegalArgumentException("Der ursprüngliche Stempel gehört nicht zu diesem Benutzer und Tag.");
            }
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

        req.captureOriginalDay(entriesForDay(user, desiredTimestamp.toLocalDate()));
        // Keep the old values on the request, without preventing later replacement
        // of the live time entries by an approved correction or an admin edit.
        req.setTargetEntry(null);

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
        if (CompanyScope.selection() != null) {
            return correctionRepo.findAllByCompanyId(CompanyScope.companyFor(requestingUser).getId());
        }

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
            if (CompanyScope.companyFor(requestingUser) == null) {
                // Admin ohne Firma kann keine firmenbezogenen Anträge sehen.
                return Collections.emptyList();
            }
            Long companyId = CompanyScope.companyFor(requestingUser).getId();
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

    @Transactional(isolation = Isolation.READ_COMMITTED) // DIESE ZEILE IST DIE LÖSUNG
    public CorrectionRequest approveRequest(Long requestId, String comment, String adminUsername) {
        // Der Rest der Methode bleibt exakt gleich wie in meinem vorherigen Vorschlag.
        // Ich füge sie hier der Vollständigkeit halber nochmals ein.

        CorrectionRequest initialRequest = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));

        User adminUser = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new SecurityException("Admin not found: " + adminUsername));
        CompanyScope.requireTarget(initialRequest.getUser());
        if (!accessControlService.isSuperAdmin(adminUser)) {
            if (!accessControlService.isAdmin(adminUser)
                    || !accessControlService.sameCompany(adminUser, initialRequest.getUser())) {
                throw new SecurityException("Admin cannot approve correction requests from another company.");
            }
        }

        Long userId = initialRequest.getUser().getId();

        User targetUser = userRepo.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer mit ID " + userId + " nicht gefunden."));

        entityManager.clear(); // Dies schadet nicht und sorgt für zusätzliche Sicherheit auf JPA-Ebene.

        initialRequest = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found after lock."));

        if (initialRequest.isApproved() || initialRequest.isDenied()) {
            logger.warn("Admin {}: Korrekturantrag ID {} wurde bereits von einer anderen Transaktion bearbeitet. Überspringe doppelte Genehmigung.",
                    adminUsername, requestId);
            return initialRequest;
        }

        LocalDate correctionDate = initialRequest.getDesiredTimestamp().toLocalDate();
        LocalDateTime startOfDay = correctionDate.atStartOfDay();
        LocalDateTime endOfDay = correctionDate.plusDays(1).atStartOfDay().minusNanos(1);

        List<CorrectionRequest> allRequestsForDay = correctionRepo.findByUserAndDesiredTimestampBetweenAndApprovedIsFalseAndDeniedIsFalse(
                targetUser, startOfDay, endOfDay);

        if (allRequestsForDay.isEmpty()) {
            throw new IllegalStateException("Keine gültigen Korrekturanfragen für den Tag gefunden.");
        }

        List<TimeTrackingEntry> entriesToDelete = entriesForDay(targetUser, correctionDate);
        for (CorrectionRequest req : allRequestsForDay) {
            req.captureOriginalDay(entriesToDelete);
        }
        if (!entriesToDelete.isEmpty()) {
            // Also preserve older requests that still hold live foreign keys.
            for (CorrectionRequest linked : correctionRepo.findByTargetEntryIn(entriesToDelete)) {
                linked.setTargetEntry(linked.getTargetEntry());
                linked.setTargetEntry(null);
                correctionRepo.save(linked);
            }
            correctionRepo.flush();
            timeTrackingEntryRepo.deleteAll(entriesToDelete);
            timeTrackingEntryRepo.flush();
            logger.info("Admin {}: {} bestehende Einträge für Benutzer {} am {} gelöscht.",
                    adminUsername, entriesToDelete.size(), targetUser.getUsername(), correctionDate);
        }

        for (CorrectionRequest req : allRequestsForDay) {
            TimeTrackingEntry newEntry = new TimeTrackingEntry(
                    req.getUser(),
                    req.getDesiredTimestamp(),
                    req.getDesiredPunchType(),
                    TimeTrackingEntry.PunchSource.USER_CORRECTION
            );
            newEntry.setCorrectedByUser(true);
            newEntry.setSystemGeneratedNote("Korrektur genehmigt von " + adminUsername + ".");
            newEntry.setCorrectionAdminUsername(adminUser.getUsername());
            newEntry.setCorrectionAdminInitials(UserInitials.from(adminUser));
            timeTrackingEntryRepo.save(newEntry);
            logger.info("Admin {}: Neuen korrigierten TimeTrackingEntry für Benutzer {} erstellt: {} {}",
                    adminUsername, targetUser.getUsername(), newEntry.getEntryTimestamp(), newEntry.getPunchType());
        }

        for (CorrectionRequest req : allRequestsForDay) {
            req.setApproved(true);
            req.setDenied(false);
            req.setAdminComment(comment);
            req.setProcessedByAdminUsername(adminUser.getUsername());
            req.setProcessedByAdminInitials(UserInitials.from(adminUser));
            correctionRepo.save(req);
        }

        timeTrackingService.rebuildUserBalance(targetUser);

        return initialRequest;
    }

    @Transactional(readOnly = true)
    public List<AdminCorrectionRequestDTO> getAllRequestsForAdminDashboard() {
        return correctionRepo.findAllWithDetails()
                .stream()
                .filter(request -> !isSuperAdminUser(request.getUser()))
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AdminCorrectionRequestDTO> getRequestsByCompanyForAdminDashboard(Long companyId) {
        return correctionRepo.findAllByCompanyId(companyId)
                .stream()
                .filter(request -> !isSuperAdminUser(request.getUser()))
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CorrectionRequest denyRequest(Long requestId, String comment) {
        return denyRequest(requestId, comment, null);
    }

    @Transactional
    public CorrectionRequest denyRequest(Long requestId, String comment, String adminUsername) {
        CorrectionRequest req = correctionRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Correction Request with ID " + requestId + " not found"));
        User adminUser = null;
        CompanyScope.requireTarget(req.getUser());
        if (adminUsername != null) {
            adminUser = userRepo.findByUsername(adminUsername)
                    .orElseThrow(() -> new SecurityException("Admin not found: " + adminUsername));
            if (!accessControlService.isSuperAdmin(adminUser)) {
                if (!accessControlService.isAdmin(adminUser)
                        || !accessControlService.sameCompany(adminUser, req.getUser())) {
                    throw new SecurityException("Admin cannot deny correction requests from another company.");
                }
            }
        }
        if (req.isApproved() || req.isDenied()) {
            throw new RuntimeException("Request with ID " + requestId + " has already been processed.");
        }
        req.setDenied(true);
        req.setApproved(false);
        req.setAdminComment(comment);
        if (adminUser != null) {
            req.setProcessedByAdminUsername(adminUser.getUsername());
            req.setProcessedByAdminInitials(UserInitials.from(adminUser));
        }
        return correctionRepo.save(req);
    }

    public UserRepository getUserRepo() { return userRepo; }

    private boolean isSuperAdminUser(User user) {
        return user != null
                && user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }

    private AdminCorrectionRequestDTO toAdminDto(CorrectionRequest request) {
        AdminCorrectionRequestDTO dto = new AdminCorrectionRequestDTO(
                request.getId(),
                request.getUsername(),
                request.getRequestDate(),
                request.getDesiredTimestamp(),
                request.getDesiredPunchType(),
                request.getReason(),
                request.isApproved(),
                request.isDenied(),
                request.getAdminComment(),
                request.getProcessedByAdminUsername(),
                request.getProcessedByAdminInitials(),
                request.getOriginalTimestamp(),
                request.getOriginalPunchType(),
                request.getTargetEntryId()
        );
        if (request.isOriginalDayCaptured()) {
            dto.setOriginalDayEntries(request.getOriginalDayEntries());
        } else if (!request.isApproved() && !request.isDenied() && request.getDesiredTimestamp() != null) {
            // Legacy requests without an entry ID: show the real current day,
            // never guess a pairing or reconstruct history from corrected times.
            dto.setOriginalDayEntries(entriesForDay(request.getUser(), request.getDesiredTimestamp().toLocalDate())
                    .stream().map(CorrectionOriginalEntry::new).toList());
        }
        return dto;
    }

    private List<TimeTrackingEntry> entriesForDay(User user, LocalDate day) {
        return timeTrackingEntryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(
                user, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }
}
