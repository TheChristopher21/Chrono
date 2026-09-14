package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.OperationalAlert;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery.Channel;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PmsOperationalAlertDeliveryRepository;
import com.chrono.chrono.services.pms.PmsOperationalAlertDeliveryService.DeliveryBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Import(PmsOperationalAlertDeliveryService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PmsOperationalAlertDeliveryIntegrationTest {
    @Autowired CompanyRepository companies;
    @Autowired HotelPropertyRepository properties;
    @Autowired PmsOperationalAlertDeliveryRepository deliveries;
    @Autowired PmsOperationalAlertDeliveryService service;
    @Autowired PlatformTransactionManager transactions;
    private HotelProperty property;
    private Company company;
    private final LocalDateTime checkedAt = LocalDateTime.of(2026, 9, 14, 10, 0);
    private final Map<Channel, String> email = Map.of(Channel.EMAIL, "operations@example.com");

    @BeforeEach
    void setup() {
        company = companies.saveAndFlush(new Company("Alarm test " + UUID.randomUUID()));
        property = hotel("A");
    }

    @Test
    void acknowledgementSurvivesNewServiceInstanceAndChangedCountsOnlySendNewCodes() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "No backup");
        var first = claim(checkedAt, backup);
        service.delivered(first.get(0));
        var restarted = new PmsOperationalAlertDeliveryService(properties, deliveries, Duration.ofMinutes(10));
        var newAlerts = new TransactionTemplate(transactions).execute(status -> restarted.claim(
                company.getId(), property.getId(), health(checkedAt.plusMinutes(1),
                        alert("BACKUP", HealthStatus.CRITICAL, "A different backup file"),
                        alert("OUTBOX", HealthStatus.WARNING, "2 errors")), email));

        assertThat(newAlerts).hasSize(1);
        assertThat(newAlerts.get(0).alerts()).extracting(item -> item.alert().code()).containsExactly("OUTBOX");
        service.delivered(newAlerts.get(0));
        assertThat(claim(checkedAt.plusMinutes(2), backup,
                alert("OUTBOX", HealthStatus.WARNING, "3 errors"))).isEmpty();
    }

    @Test
    void resolvedCodeCanRecurWithoutRepeatingOtherPersistentAlerts() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "Backup");
        var outbox = alert("OUTBOX", HealthStatus.WARNING, "Outbox");
        service.delivered(claim(checkedAt, backup, outbox).get(0));
        assertThat(claim(checkedAt.plusMinutes(1), backup)).isEmpty();

        var recurring = claim(checkedAt.plusMinutes(2), backup, outbox);

        assertThat(recurring.get(0).alerts()).extracting(item -> item.alert().code()).containsExactly("OUTBOX");
    }

    @Test
    void sendsEscalationOnceButDoesNotRepeatPreviouslyNotifiedSeverityInSameEpisode() {
        service.delivered(claim(checkedAt, alert("BACKUP", HealthStatus.WARNING, "Old backup")).get(0));
        var escalated = claim(checkedAt.plusMinutes(1), alert("BACKUP", HealthStatus.CRITICAL, "Invalid backup"));
        assertThat(escalated).hasSize(1);
        service.delivered(escalated.get(0));
        assertThat(claim(checkedAt.plusMinutes(2), alert("BACKUP", HealthStatus.WARNING, "Old backup"))).isEmpty();
        assertThat(claim(checkedAt.plusMinutes(3), alert("BACKUP", HealthStatus.CRITICAL, "Invalid backup"))).isEmpty();
    }

    @Test
    void failedChannelRemainsRetryableWithoutReplayingSuccessfullyDeliveredChannel() {
        var destinations = Map.of(Channel.EMAIL, "operations@example.com", Channel.TEAMS, "https://example.com/webhook");
        var health = health(checkedAt, alert("BACKUP", HealthStatus.CRITICAL, "Backup"));
        var batches = service.claim(company.getId(), property.getId(), health, destinations);
        service.delivered(batches.stream().filter(batch -> batch.channel() == Channel.EMAIL).findFirst().orElseThrow());
        service.failed(batches.stream().filter(batch -> batch.channel() == Channel.TEAMS).findFirst().orElseThrow());

        var retry = service.claim(company.getId(), property.getId(), health, destinations);

        assertThat(retry).extracting(DeliveryBatch::channel).containsExactly(Channel.TEAMS);
    }

    @Test
    void unacknowledgedLeaseExpiresAndOldSenderCannotAcknowledgeTheNewClaim() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "Backup");
        var abandoned = claim(checkedAt, backup).get(0);
        assertThat(claim(checkedAt, backup)).isEmpty();
        var row = deliveries.findAllByProperty_Id(property.getId()).get(0);
        row.setClaimUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        deliveries.saveAndFlush(row);

        var retry = claim(checkedAt, backup).get(0);
        service.delivered(abandoned);

        var stillPending = deliveries.findById(row.getId()).orElseThrow();
        assertThat(stillPending.getNotifiedSeverity()).isZero();
        assertThat(stillPending.getClaimToken()).isEqualTo(retry.alerts().get(0).token());
        service.delivered(retry);
        assertThat(claim(checkedAt, backup)).isEmpty();
    }

    @Test
    void changedDestinationDoesNotInheritOldRecipientsAcknowledgementOrStoreTheirAddress() {
        var health = health(checkedAt, alert("BACKUP", HealthStatus.CRITICAL, "Backup"));
        service.delivered(service.claim(company.getId(), property.getId(), health, email).get(0));

        var newRecipient = service.claim(company.getId(), property.getId(), health,
                Map.of(Channel.EMAIL, "new-operations@example.com"));

        assertThat(newRecipient).hasSize(1);
        assertThat(deliveries.findAllByProperty_Id(property.getId()).get(0).getDestinationHash())
                .hasSize(64).doesNotContain("@", "operations");
    }

    @Test
    void hotelsKeepIndependentNotificationState() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "Backup");
        service.delivered(claim(checkedAt, backup).get(0));
        var second = hotel("B");

        assertThat(service.claim(company.getId(), second.getId(),
                new PmsOperationalHealthResponse(second.getId(), HealthStatus.CRITICAL, checkedAt,
                        0, 0, 0, List.of(), List.of(backup)), email)).hasSize(1);
    }

    @Test
    void staleHealthyObservationCannotClearNewerAcknowledgedEpisode() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "Backup");
        service.delivered(claim(checkedAt.plusMinutes(1), backup).get(0));
        assertThat(claim(checkedAt)).isEmpty();

        assertThat(claim(checkedAt.plusMinutes(2), backup)).isEmpty();
    }

    @Test
    void acknowledgementFromResolvedEpisodeCannotMuteRecurrence() {
        var backup = alert("BACKUP", HealthStatus.CRITICAL, "Backup");
        var old = claim(checkedAt, backup).get(0);
        claim(checkedAt.plusMinutes(1));
        var recurring = claim(checkedAt.plusMinutes(2), backup).get(0);
        service.delivered(old);

        var row = deliveries.findAllByProperty_Id(property.getId()).get(0);
        assertThat(row.getNotifiedSeverity()).isZero();
        assertThat(row.getClaimToken()).isEqualTo(recurring.alerts().get(0).token());
    }

    @Test
    void competingSchedulersOnlyReserveOneDelivery() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                return claim(checkedAt, alert("BACKUP", HealthStatus.CRITICAL, "Backup"));
            });
            var second = executor.submit(() -> {
                start.await();
                return claim(checkedAt, alert("BACKUP", HealthStatus.CRITICAL, "Backup"));
            });
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS).size() + second.get(15, TimeUnit.SECONDS).size()).isEqualTo(1);
            assertThat(deliveries.findAllByProperty_Id(property.getId())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private HotelProperty hotel(String code) {
        HotelProperty hotel = new HotelProperty();
        hotel.setCompany(company);
        hotel.setCode(code);
        hotel.setName("Synthetic alert hotel " + code);
        return properties.saveAndFlush(hotel);
    }

    private List<DeliveryBatch> claim(LocalDateTime timestamp, OperationalAlert... alerts) {
        return service.claim(company.getId(), property.getId(), health(timestamp, alerts), email);
    }

    private PmsOperationalHealthResponse health(LocalDateTime timestamp, OperationalAlert... alerts) {
        return new PmsOperationalHealthResponse(property.getId(), alerts.length == 0 ? HealthStatus.OK : HealthStatus.CRITICAL,
                timestamp, 0, 0, 0, List.of(), List.of(alerts));
    }

    private OperationalAlert alert(String code, HealthStatus severity, String details) {
        return new OperationalAlert(code, severity, code, details, "Check configuration");
    }
}
