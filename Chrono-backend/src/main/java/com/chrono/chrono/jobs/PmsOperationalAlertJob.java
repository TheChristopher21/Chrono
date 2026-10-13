package com.chrono.chrono.jobs;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery.Channel;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.ExternalNotificationService;
import com.chrono.chrono.services.pms.PmsOperationalHealthService;
import com.chrono.chrono.services.pms.PmsOperationalAlertDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.pms.alerts.enabled", havingValue = "true")
public class PmsOperationalAlertJob {
    private static final Logger logger = LoggerFactory.getLogger(PmsOperationalAlertJob.class);
    private final HotelPropertyRepository propertyRepository;
    private final PmsOperationalHealthService healthService;
    private final ExternalNotificationService notificationService;
    private final EmailService emailService;
    private final String alertEmail;
    private final PmsOperationalAlertDeliveryService deliveryService;

    public PmsOperationalAlertJob(HotelPropertyRepository propertyRepository,
                                  PmsOperationalHealthService healthService,
                                  ExternalNotificationService notificationService,
                                  EmailService emailService,
                                  PmsOperationalAlertDeliveryService deliveryService,
                                  @Value("${app.pms.alerts.email:}") String alertEmail) {
        this.propertyRepository = propertyRepository;
        this.healthService = healthService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.deliveryService = deliveryService;
        this.alertEmail = alertEmail == null ? "" : alertEmail.trim();
    }

    @Scheduled(
            fixedDelayString = "${app.pms.alerts.interval-ms:300000}",
            initialDelayString = "${app.pms.alerts.initial-delay-ms:60000}")
    public void dispatchAlerts() {
        for (HotelProperty property : propertyRepository.findAllWithCompany()) {
            try {
                dispatchProperty(property);
            } catch (RuntimeException exception) {
                // One failing hotel must not prevent notifications for the remaining portfolio.
                logger.error("PMS alarm check failed for property {} ({})", property.getId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private void dispatchProperty(HotelProperty property) {
        PmsOperationalHealthResponse health = healthService.health(property.getCompany(), property.getId());
        Map<Channel, String> destinations = new EnumMap<>(Channel.class);
        destinations.put(Channel.EMAIL, alertEmail);
        destinations.put(Channel.SLACK, property.getCompany().getSlackWebhookUrl());
        destinations.put(Channel.TEAMS, property.getCompany().getTeamsWebhookUrl());
        var batches = deliveryService.claim(property.getCompany().getId(), property.getId(), health, destinations);
        for (var batch : batches) {
            String message = formatMessage(property, health,
                    batch.alerts().stream().map(PmsOperationalAlertDeliveryService.ClaimedAlert::alert).toList());
            try {
                if (batch.channel() == Channel.EMAIL) {
                    emailService.sendOperationalAlertChecked(batch.destination(),
                            "Chrono PMS Betriebsalarm: " + property.getName(), message);
                } else {
                    notificationService.sendOperationalWebhookChecked(batch.destination(), message);
                }
            } catch (RuntimeException exception) {
                deliveryService.failed(batch);
                logger.error("PMS alarm delivery failed for property {} via {} ({}); retry scheduled",
                        property.getId(), batch.channel(), exception.getClass().getSimpleName());
                continue;
            }
            deliveryService.delivered(batch);
        }
    }

    private String formatMessage(HotelProperty property, PmsOperationalHealthResponse health,
                                 List<PmsOperationalHealthResponse.OperationalAlert> changedAlerts) {
        String alerts = changedAlerts.stream()
                .map(alert -> "- [" + alert.severity() + "] " + alert.title() + ": "
                        + alert.details() + " Maßnahme: " + alert.recommendedAction())
                .collect(Collectors.joining(System.lineSeparator()));
        return "Chrono PMS – " + property.getName() + System.lineSeparator()
                + "Gesamtstatus: " + health.status() + System.lineSeparator()
                + "Neue oder eskalierte Alarme:" + System.lineSeparator()
                + alerts;
    }
}
