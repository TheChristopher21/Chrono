package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.OutboxStatus;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.IntegrationOutboxRepository;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmsOperationalHealthServiceTest {
    private final HotelPropertyRepository propertyRepository = mock(HotelPropertyRepository.class);
    private final IntegrationOutboxRepository outboxRepository = mock(IntegrationOutboxRepository.class);
    private final PmsAuditEventRepository auditRepository = mock(PmsAuditEventRepository.class);
    private final PmsAuditWriter auditWriter = mock(PmsAuditWriter.class);
    private final PmsBackupVerifier backupVerifier = mock(PmsBackupVerifier.class);
    private final PmsRestoreDrillService restoreDrillService = mock(PmsRestoreDrillService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Company company = mock(Company.class);
    private PmsOperationalHealthService service;

    @BeforeEach
    void setUp() {
        when(company.getId()).thenReturn(4L);
        when(propertyRepository.findByIdAndCompany_Id(7L, 4L))
                .thenReturn(Optional.of(mock(HotelProperty.class)));
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(auditRepository.findTop100ByProperty_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(backupVerifier.inspect()).thenReturn(new PmsBackupVerifier.BackupCheck(
                HealthStatus.OK, "Backup geprüft.", null, true));
        when(restoreDrillService.inspect()).thenReturn(new PmsRestoreDrillService.RestoreCheck(
                HealthStatus.OK, "Restore geprüft.", null));
        service = new PmsOperationalHealthService(
                propertyRepository, outboxRepository, auditRepository, auditWriter,
                backupVerifier, restoreDrillService, jdbcTemplate, Duration.ofMinutes(15));
    }

    @Test
    void reportsHealthyOperationalFoundation() {
        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.status()).isEqualTo(HealthStatus.OK);
        assertThat(response.components()).extracting(PmsOperationalHealthResponse.ComponentHealth::key)
                .containsExactly("database", "outbox", "audit", "backup", "restore");
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void raisesCriticalAlertForDeadLetterEvents() {
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.DEAD_LETTER))
                .thenReturn(2L);

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(response.deadLetterEvents()).isEqualTo(2);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_OUTBOX_DEAD_LETTER");
    }

    @Test
    void raisesCriticalAlertWhenDatabaseProbeFails() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("offline"));

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_DATABASE_UNAVAILABLE");
    }
}
