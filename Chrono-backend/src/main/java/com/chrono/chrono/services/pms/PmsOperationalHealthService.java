package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.ComponentHealth;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.OperationalAlert;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.IntegrationOutboxEvent;
import com.chrono.chrono.entities.pms.OutboxStatus;
import com.chrono.chrono.entities.pms.PmsAuditEvent;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.IntegrationOutboxRepository;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PmsOperationalHealthService {
    private final HotelPropertyRepository propertyRepository;
    private final IntegrationOutboxRepository outboxRepository;
    private final PmsAuditEventRepository auditRepository;
    private final PmsAuditWriter auditWriter;
    private final PmsBackupVerifier backupVerifier;
    private final PmsRestoreDrillService restoreDrillService;
    private final JdbcTemplate jdbcTemplate;
    private final Duration pendingWarningAge;

    public PmsOperationalHealthService(
            HotelPropertyRepository propertyRepository,
            IntegrationOutboxRepository outboxRepository,
            PmsAuditEventRepository auditRepository,
            PmsAuditWriter auditWriter,
            PmsBackupVerifier backupVerifier,
            PmsRestoreDrillService restoreDrillService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.pms.monitoring.pending-warning-age:PT15M}") Duration pendingWarningAge) {
        this.propertyRepository = propertyRepository;
        this.outboxRepository = outboxRepository;
        this.auditRepository = auditRepository;
        this.auditWriter = auditWriter;
        this.backupVerifier = backupVerifier;
        this.restoreDrillService = restoreDrillService;
        this.jdbcTemplate = jdbcTemplate;
        this.pendingWarningAge = pendingWarningAge;
    }

    @Transactional(readOnly = true)
    public PmsOperationalHealthResponse health(Company company, Long propertyId) {
        propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hotelbetrieb nicht gefunden."));
        LocalDateTime checkedAt = LocalDateTime.now().withNano(0);
        List<ComponentHealth> components = new ArrayList<>();
        List<OperationalAlert> alerts = new ArrayList<>();

        HealthStatus databaseStatus = databaseHealth();
        components.add(new ComponentHealth(
                "database", "Datenbank", databaseStatus,
                databaseStatus == HealthStatus.OK
                        ? "Datenbankabfrage erfolgreich."
                        : "Die Datenbankabfrage ist fehlgeschlagen.",
                checkedAt));
        if (databaseStatus == HealthStatus.CRITICAL) {
            alerts.add(new OperationalAlert(
                    "PMS_DATABASE_UNAVAILABLE", HealthStatus.CRITICAL,
                    "PMS-Datenbank nicht erreichbar",
                    "Die Betriebsdaten können aktuell nicht zuverlässig gelesen werden.",
                    "Datenbankverbindung und MySQL-Dienst sofort prüfen."));
        }

        long pending = outboxRepository.countByProperty_IdAndStatus(propertyId, OutboxStatus.PENDING);
        long failed = outboxRepository.countByProperty_IdAndStatus(propertyId, OutboxStatus.FAILED);
        long deadLetter = outboxRepository.countByProperty_IdAndStatus(propertyId, OutboxStatus.DEAD_LETTER);
        IntegrationOutboxEvent oldestOpen = outboxRepository
                .findFirstByProperty_IdAndStatusInOrderByCreatedAtAsc(
                        propertyId, List.of(OutboxStatus.PENDING, OutboxStatus.FAILED))
                .orElse(null);
        boolean overdue = oldestOpen != null
                && oldestOpen.getCreatedAt().isBefore(checkedAt.minus(pendingWarningAge));
        HealthStatus outboxStatus = deadLetter > 0
                ? HealthStatus.CRITICAL
                : failed > 0 || overdue ? HealthStatus.WARNING : HealthStatus.OK;
        components.add(new ComponentHealth(
                "outbox", "Integrationsqueue", outboxStatus,
                pending + " offen, " + failed + " fehlgeschlagen, " + deadLetter + " Dead-Letter.",
                checkedAt));
        if (deadLetter > 0) {
            alerts.add(new OperationalAlert(
                    "PMS_OUTBOX_DEAD_LETTER", HealthStatus.CRITICAL,
                    "Integrationsereignisse endgültig fehlgeschlagen",
                    deadLetter + " Ereignis(se) liegen in der Dead-Letter-Queue.",
                    "Integration Control Center öffnen, Fehler prüfen und Ereignisse erneut einplanen."));
        } else if (failed > 0 || overdue) {
            alerts.add(new OperationalAlert(
                    "PMS_OUTBOX_DELAYED", HealthStatus.WARNING,
                    "Integrationsqueue benötigt Aufmerksamkeit",
                    failed > 0
                            ? failed + " Zustellversuch(e) sind fehlgeschlagen."
                            : "Das älteste offene Ereignis überschreitet das Zeitlimit.",
                    "Providerstatus und ausstehende Ereignisse prüfen."));
        }

        List<PmsAuditEvent> auditEvents =
                auditRepository.findTop100ByProperty_IdOrderByCreatedAtDesc(propertyId);
        long invalidAuditEvents = auditEvents.stream()
                .filter(event -> !auditWriter.hasValidIntegrityHash(event))
                .count();
        HealthStatus auditStatus = invalidAuditEvents == 0 ? HealthStatus.OK : HealthStatus.CRITICAL;
        components.add(new ComponentHealth(
                "audit", "Audit-Integrität", auditStatus,
                auditEvents.size() + " letzte Ereignisse geprüft, " + invalidAuditEvents + " ungültig.",
                checkedAt));
        if (invalidAuditEvents > 0) {
            alerts.add(new OperationalAlert(
                    "PMS_AUDIT_INTEGRITY", HealthStatus.CRITICAL,
                    "Audit-Integrität verletzt",
                    invalidAuditEvents + " Audit-Ereignis(se) haben eine ungültige Prüfsumme.",
                    "Schreibzugriffe stoppen und Datenbankprüfung durchführen."));
        }

        PmsBackupVerifier.BackupCheck backup = backupVerifier.inspect();
        components.add(new ComponentHealth(
                "backup", "Datensicherung", backup.status(), backup.summary(),
                backup.latestBackupAt() == null ? checkedAt : backup.latestBackupAt()));
        if (backup.status() == HealthStatus.CRITICAL || backup.status() == HealthStatus.WARNING) {
            alerts.add(new OperationalAlert(
                    "PMS_BACKUP_NOT_READY", backup.status(),
                    "Datensicherung nicht vollständig bereit",
                    backup.summary(),
                    "Backup-Konfiguration, Sicherungsdatei und SHA-256-Prüfsumme prüfen."));
        }

        PmsRestoreDrillService.RestoreCheck restore = restoreDrillService.inspect();
        components.add(new ComponentHealth(
                "restore", "Restore-Probelauf", restore.status(), restore.summary(),
                restore.verifiedAt() == null ? checkedAt : restore.verifiedAt()));
        if (restore.status() == HealthStatus.CRITICAL || restore.status() == HealthStatus.WARNING) {
            alerts.add(new OperationalAlert(
                    "PMS_RESTORE_NOT_READY", restore.status(),
                    "Restore-Probelauf nicht vollständig bereit",
                    restore.summary(),
                    "Isolierte Restore-Testdatenbank und letzten Wiederherstellungsnachweis prüfen."));
        }

        HealthStatus overall = overallStatus(components);
        return new PmsOperationalHealthResponse(
                propertyId, overall, checkedAt, pending, failed, deadLetter,
                List.copyOf(components), List.copyOf(alerts));
    }

    private HealthStatus databaseHealth() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(value) ? HealthStatus.OK : HealthStatus.CRITICAL;
        } catch (RuntimeException exception) {
            return HealthStatus.CRITICAL;
        }
    }

    private HealthStatus overallStatus(List<ComponentHealth> components) {
        if (components.stream().anyMatch(component -> component.status() == HealthStatus.CRITICAL)) {
            return HealthStatus.CRITICAL;
        }
        if (components.stream().anyMatch(component ->
                component.status() == HealthStatus.WARNING
                        || component.status() == HealthStatus.NOT_CONFIGURED)) {
            return HealthStatus.WARNING;
        }
        return HealthStatus.OK;
    }
}
