package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.IntegrationOutboxEvent;
import com.chrono.chrono.entities.pms.OutboxStatus;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.IntegrationOutboxRepository;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private final PmsOutboxTransport transport = mock(PmsOutboxTransport.class);
    private PmsOperationalHealthService service;

    @BeforeEach
    void setUp() {
        when(company.getId()).thenReturn(4L);
        when(propertyRepository.findByIdAndCompany_Id(7L, 4L))
                .thenReturn(Optional.of(mock(HotelProperty.class)));
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(auditRepository.findTop100ByProperty_IdOrderBySequenceNumberDescCreatedAtDesc(7L)).thenReturn(List.of());
        when(auditWriter.hasValidChain(List.of())).thenReturn(true);
        when(backupVerifier.inspect()).thenReturn(new PmsBackupVerifier.BackupCheck(
                HealthStatus.OK, "Backup geprüft.", null, true));
        when(restoreDrillService.inspect()).thenReturn(new PmsRestoreDrillService.RestoreCheck(
                HealthStatus.OK, "Restore geprüft.", null));
        when(transport.supports(any())).thenReturn(true);
        service = serviceWithTransports(List.of(transport));
    }

    private PmsOperationalHealthService serviceWithTransports(List<PmsOutboxTransport> transports) {
        return new PmsOperationalHealthService(
                propertyRepository, outboxRepository, auditRepository, auditWriter,
                backupVerifier, restoreDrillService, jdbcTemplate, transports, Duration.ofMinutes(15));
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
        service = serviceWithTransports(List.of());
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.DEAD_LETTER))
                .thenReturn(2L);

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(response.deadLetterEvents()).isEqualTo(2);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_OUTBOX_DEAD_LETTER");
    }

    @Test
    void retainsPendingEventsWithoutAlertWhenNoProviderIsConfigured() {
        service = serviceWithTransports(List.of());
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.PENDING)).thenReturn(3L);

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.pendingEvents()).isEqualTo(3L);
        assertThat(response.alerts()).isEmpty();
        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
        assertThat(outboxComponent(response).summary()).contains("3 offen", "Keine externe Anbieteranbindung",
                "bleiben gespeichert");
        verify(outboxRepository, never()).findTop100ByProperty_IdAndStatusOrderByCreatedAtAsc(anyLong(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void reportsMissingProviderEvenBeforeFirstPendingEvent() {
        service = serviceWithTransports(List.of());

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void retainsFailureAlertEvenWhenProviderWasDisabled() {
        service = serviceWithTransports(List.of());
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.FAILED)).thenReturn(1L);

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_OUTBOX_DELAYED");
    }

    @Test
    void alertsWhenPendingEventWithMatchingProviderIsOverdue() {
        pendingEvents(pendingEvent("BOOKING_CREATED", 30));

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_OUTBOX_DELAYED");
        verify(transport).supports(new PmsOutboxMessage(12L, 7L, "BOOKING_CREATED", "RESERVATION", "25", "{}", 1));
    }

    @Test
    void doesNotTreatAnOverdueUnsupportedEventAsDeliveryFailure() {
        when(transport.supports(any())).thenReturn(false);
        pendingEvents(pendingEvent("UNSUPPORTED", 30));

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
        assertThat(outboxComponent(response).summary()).contains("1 der geprüften", "bleiben gespeichert");
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void overdueUnsupportedEventDoesNotHideLaterSupportedDeliveryDelay() {
        when(transport.supports(any())).thenAnswer(invocation ->
                "BOOKING_CREATED".equals(invocation.<PmsOutboxMessage>getArgument(0).eventType()));
        pendingEvents(pendingEvent("UNSUPPORTED", 60), pendingEvent("BOOKING_CREATED", 30));

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(response.alerts()).extracting(PmsOperationalHealthResponse.OperationalAlert::code)
                .contains("PMS_OUTBOX_DELAYED");
    }

    @Test
    void doesNotWarnAboutFreshSupportedEventsBesideOldUnsupportedEvents() {
        when(transport.supports(any())).thenAnswer(invocation ->
                "BOOKING_CREATED".equals(invocation.<PmsOutboxMessage>getArgument(0).eventType()));
        pendingEvents(pendingEvent("UNSUPPORTED", 60), pendingEvent("BOOKING_CREATED", 1));

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(outboxComponent(response).status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void keepsProviderProbeBoundedAndExplainsPartialInspection() {
        List<IntegrationOutboxEvent> oldest = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> pendingEvent("BOOKING_CREATED", 1)).toList();
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.PENDING)).thenReturn(100_000L);
        when(outboxRepository.findTop100ByProperty_IdAndStatusOrderByCreatedAtAsc(7L, OutboxStatus.PENDING))
                .thenReturn(oldest);

        PmsOperationalHealthResponse response = service.health(company, 7L);

        assertThat(response.pendingEvents()).isEqualTo(100_000L);
        assertThat(outboxComponent(response).summary()).contains("100 ältesten offenen Ereignisse");
        verify(outboxRepository).findTop100ByProperty_IdAndStatusOrderByCreatedAtAsc(7L, OutboxStatus.PENDING);
        verify(outboxRepository, never()).findAllByProperty_IdOrderByCreatedAtDesc(anyLong());
        verify(outboxRepository, never()).findFirstByProperty_IdAndStatusInOrderByCreatedAtAsc(anyLong(), any());
    }

    private void pendingEvents(IntegrationOutboxEvent... events) {
        when(outboxRepository.countByProperty_IdAndStatus(7L, OutboxStatus.PENDING)).thenReturn((long) events.length);
        when(outboxRepository.findTop100ByProperty_IdAndStatusOrderByCreatedAtAsc(7L, OutboxStatus.PENDING))
                .thenReturn(List.of(events));
    }

    private IntegrationOutboxEvent pendingEvent(String eventType, int ageMinutes) {
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setId(12L);
        event.setEventType(eventType);
        event.setAggregateType("RESERVATION");
        event.setAggregateId("25");
        event.setPayload("{}");
        event.setCreatedAt(LocalDateTime.now().minusMinutes(ageMinutes));
        return event;
    }

    private PmsOperationalHealthResponse.ComponentHealth outboxComponent(PmsOperationalHealthResponse response) {
        return response.components().stream().filter(component -> "outbox".equals(component.key())).findFirst().orElseThrow();
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
