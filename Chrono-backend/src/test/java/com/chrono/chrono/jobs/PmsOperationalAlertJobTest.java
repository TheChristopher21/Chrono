package com.chrono.chrono.jobs;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.OperationalAlert;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery.Channel;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.ExternalNotificationService;
import com.chrono.chrono.services.pms.PmsOperationalAlertDeliveryService;
import com.chrono.chrono.services.pms.PmsOperationalAlertDeliveryService.ClaimedAlert;
import com.chrono.chrono.services.pms.PmsOperationalAlertDeliveryService.DeliveryBatch;
import com.chrono.chrono.services.pms.PmsOperationalHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsOperationalAlertJobTest {
    private final HotelPropertyRepository properties = mock(HotelPropertyRepository.class);
    private final PmsOperationalHealthService healthService = mock(PmsOperationalHealthService.class);
    private final ExternalNotificationService webhooks = mock(ExternalNotificationService.class);
    private final EmailService emails = mock(EmailService.class);
    private final PmsOperationalAlertDeliveryService deliveries = mock(PmsOperationalAlertDeliveryService.class);
    private final Company company = new Company("Chrono Hotel AG");
    private final HotelProperty property = new HotelProperty();
    private final OperationalAlert backup = new OperationalAlert("BACKUP", HealthStatus.CRITICAL,
            "Datenbanksicherung", "Kein gültiges Backup", "Backup prüfen.");
    private final OperationalAlert outbox = new OperationalAlert("OUTBOX", HealthStatus.WARNING,
            "Übertragungen", "2 Zustellfehler", "Anbieter prüfen.");
    private final PmsOperationalHealthResponse health = new PmsOperationalHealthResponse(5L, HealthStatus.CRITICAL,
            LocalDateTime.now(), 2, 0, 0, List.of(), List.of(backup, outbox));

    @BeforeEach
    void setUp() {
        company.setId(3L);
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        property.setName("Chrono Zürich");
        property.setEmail("hotel@example.com");
        when(properties.findAllWithCompany()).thenReturn(List.of(property));
        when(healthService.health(company, 5L)).thenReturn(health);
    }

    @Test
    void sendsOnlyNewAlarmAndAcknowledgesAfterSuccessfulDelivery() {
        var batch = batch(Channel.EMAIL, "operations@example.com", outbox);
        when(deliveries.claim(eq(3L), eq(5L), eq(health), anyMap())).thenReturn(List.of(batch));

        job("operations@example.com").dispatchAlerts();

        var message = ArgumentCaptor.forClass(String.class);
        verify(emails).sendOperationalAlertChecked(eq("operations@example.com"),
                eq("Chrono PMS Betriebsalarm: Chrono Zürich"), message.capture());
        assertThat(message.getValue()).contains("Übertragungen", "Gesamtstatus: CRITICAL")
                .doesNotContain("Datenbanksicherung");
        var ordered = inOrder(emails, deliveries);
        ordered.verify(emails).sendOperationalAlertChecked(anyString(), anyString(), anyString());
        ordered.verify(deliveries).delivered(batch);
        verifyNoInteractions(webhooks);
    }

    @Test
    void retriesFailedEmailWithoutBlockingIndependentWebhookDelivery() {
        var email = batch(Channel.EMAIL, "operations@example.com", backup);
        var slack = batch(Channel.SLACK, "https://example.com/test-webhook", backup);
        when(deliveries.claim(eq(3L), eq(5L), eq(health), anyMap())).thenReturn(List.of(email, slack));
        doThrow(new MailSendException("SMTP unavailable")).when(emails)
                .sendOperationalAlertChecked(anyString(), anyString(), anyString());

        job("operations@example.com").dispatchAlerts();

        verify(deliveries).failed(email);
        verify(deliveries, never()).delivered(email);
        verify(webhooks).sendOperationalWebhookChecked(eq(slack.destination()), contains("Datenbanksicherung"));
        verify(deliveries).delivered(slack);
    }

    @Test
    void recordsChecksWithoutInventingAnEmailRecipient() {
        when(deliveries.claim(eq(3L), eq(5L), eq(health), anyMap())).thenReturn(List.of());

        job("").dispatchAlerts();

        verify(deliveries).claim(eq(3L), eq(5L), eq(health), argThat(destinations ->
                destinations.get(Channel.EMAIL).isBlank()));
        verifyNoInteractions(emails, webhooks);
    }

    @Test
    void continuesPortfolioAfterOneHotelHealthCheckFails() {
        HotelProperty unavailable = new HotelProperty();
        ReflectionTestUtils.setField(unavailable, "id", 4L);
        unavailable.setCompany(company);
        when(properties.findAllWithCompany()).thenReturn(List.of(unavailable, property));
        when(healthService.health(company, 4L)).thenThrow(new IllegalStateException("Unavailable"));
        var email = batch(Channel.EMAIL, "operations@example.com", backup);
        when(deliveries.claim(eq(3L), eq(5L), eq(health), anyMap())).thenReturn(List.of(email));

        job("operations@example.com").dispatchAlerts();

        verify(deliveries).delivered(email);
    }

    private PmsOperationalAlertJob job(String recipient) {
        return new PmsOperationalAlertJob(properties, healthService, webhooks, emails, deliveries, recipient);
    }

    private DeliveryBatch batch(Channel channel, String destination, OperationalAlert alert) {
        return new DeliveryBatch(3L, 5L, channel, destination,
                List.of(new ClaimedAlert(7L, "token", alert.severity() == HealthStatus.CRITICAL ? 2 : 1, alert)));
    }
}
