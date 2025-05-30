package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.SickLeave;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class SickLeaveService {

    private static final Logger logger = LoggerFactory.getLogger(SickLeaveService.class);

    @Autowired
    private SickLeaveRepository sickLeaveRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TimeTrackingService timeTrackingService; // Für Saldo-Neuberechnung

    @Transactional
    public SickLeave reportSickLeave(String reporterUsername, String targetUsername, LocalDate startDate, LocalDate endDate, boolean halfDay, String comment) {
        User reporter = userRepo.findByUsername(reporterUsername)
                .orElseThrow(() -> new UserNotFoundException("Meldender Benutzer " + reporterUsername + " nicht gefunden."));
        User targetUser = userRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("Betroffener Benutzer " + targetUsername + " nicht gefunden."));

        // Berechtigungsprüfung: Admin/SuperAdmin oder Selbstmeldung
        boolean isSelfReport = reporterUsername.equals(targetUsername);
        boolean isAdminActing = reporter.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));
        boolean isSuperAdmin = reporter.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));

        if (!isSelfReport && !isAdminActing) {
            throw new SecurityException(reporterUsername + " ist nicht berechtigt, Krankheit für " + targetUsername + " zu melden.");
        }

        if (isAdminActing && !isSuperAdmin) { // Admin, aber nicht SuperAdmin
            Company reporterCompany = reporter.getCompany();
            Company targetCompany = targetUser.getCompany();
            if (reporterCompany == null || targetCompany == null || !reporterCompany.getId().equals(targetCompany.getId())) {
                throw new SecurityException("Admin " + reporterUsername + " kann Krankheit nur für Benutzer der eigenen Firma melden.");
            }
        }
        // SuperAdmin kann für jeden melden.

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen.");
        }
        if (halfDay && !startDate.isEqual(endDate)) {
            throw new IllegalArgumentException("Halbtägige Krankmeldung nur für einen einzelnen Tag möglich.");
        }

        SickLeave sickLeave = new SickLeave();
        sickLeave.setUser(targetUser);
        sickLeave.setStartDate(startDate);
        sickLeave.setEndDate(endDate);
        sickLeave.setHalfDay(halfDay);
        sickLeave.setComment(comment);
        sickLeave.setReportedAt(LocalDate.now());

        SickLeave savedSickLeave = sickLeaveRepo.save(sickLeave);
        logger.info("Krankheit für {} (von {} bis {}, halbtags: {}) durch {} gemeldet.",
                targetUsername, startDate, endDate, halfDay, reporterUsername);

        // Saldo des betroffenen Benutzers neu berechnen, da Krankheit das Soll beeinflusst
        timeTrackingService.rebuildUserBalance(targetUser);

        return savedSickLeave;
    }

    public List<SickLeave> getSickLeavesForUser(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Benutzer " + username + " nicht gefunden."));
        return sickLeaveRepo.findByUser(user);
    }

    public List<SickLeave> getAllSickLeavesInCompany(Long companyId, User requestingAdmin) {
        if (requestingAdmin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (companyId != null) { // SuperAdmin filtert nach Firma, wenn ID gegeben
                return sickLeaveRepo.findByUser_Company_Id(companyId);
            }
            // Wenn ein SuperAdmin diesen Service-Endpunkt ohne companyId aufruft
            // (sollte durch Controller-Logik zu getAllSickLeavesForSuperAdmin gehen)
            // Aber zur Sicherheit:
            return sickLeaveRepo.findAll();
        }

        // Regulärer Admin
        if (requestingAdmin.getCompany() == null) {
            logger.warn("Admin {} ohne Firma versucht, Krankmeldungen abzurufen.", requestingAdmin.getUsername());
            return Collections.emptyList();
        }
        // Für regulären Admin: companyId-Parameter wird hier ignoriert, es zählt die Firma des Admins
        if (companyId != null && !requestingAdmin.getCompany().getId().equals(companyId)) {
            logger.warn("Admin {} versucht, Krankmeldungen einer fremden Firma (ID: {}) abzurufen. Zugriff verweigert.", requestingAdmin.getUsername(), companyId);
            throw new SecurityException("Keine Berechtigung zum Zugriff auf Krankmeldungen dieser Firma.");
        }
        return sickLeaveRepo.findByUser_Company_Id(requestingAdmin.getCompany().getId());
    }

    public List<SickLeave> getAllSickLeavesForSuperAdmin() {
        return sickLeaveRepo.findAll();
    }

    @Transactional
    public void deleteSickLeave(Long sickLeaveId, String adminUsername) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin " + adminUsername + " nicht gefunden."));
        SickLeave sickLeave = sickLeaveRepo.findById(sickLeaveId)
                .orElseThrow(() -> new RuntimeException("Krankmeldung mit ID " + sickLeaveId + " nicht gefunden."));

        User targetUser = sickLeave.getUser();
        if (targetUser == null) { // Sollte dank nullable=false in der Entität nicht passieren
            logger.error("Krankmeldung ID {} hat keinen zugeordneten Benutzer. Löschen nicht sicher möglich.", sickLeaveId);
            throw new IllegalStateException("Krankmeldung ohne Benutzer kann nicht sicher verarbeitet werden.");
        }


        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin) { // Wenn kein SuperAdmin, dann muss es ein Admin der gleichen Firma sein
            Company adminCompany = admin.getCompany();
            Company targetUserCompany = targetUser.getCompany();
            if (adminCompany == null || targetUserCompany == null || !adminCompany.getId().equals(targetUserCompany.getId())) {
                logger.warn("Admin {} (Firma: {}) versuchte Krankmeldung ID {} von User {} (Firma: {}) zu löschen. Zugriff verweigert.",
                        adminUsername,
                        adminCompany != null ? adminCompany.getId() : "KEINE",
                        sickLeaveId,
                        targetUser.getUsername(),
                        targetUserCompany != null ? targetUserCompany.getId() : "KEINE");
                throw new SecurityException("Admin " + adminUsername + " ist nicht berechtigt, diese Krankmeldung zu löschen.");
            }
        }
        // SuperAdmin darf immer löschen

        sickLeaveRepo.delete(sickLeave);
        logger.info("Krankmeldung ID {} für Benutzer {} durch Admin {} gelöscht.", sickLeaveId, targetUser.getUsername(), adminUsername);

        // Saldo des betroffenen Benutzers neu berechnen, da die gelöschte Krankheit das Soll wieder beeinflusst
        timeTrackingService.rebuildUserBalance(targetUser);
    }
}