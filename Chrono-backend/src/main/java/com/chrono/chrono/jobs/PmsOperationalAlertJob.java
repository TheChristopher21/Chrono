package com.chrono.chrono.jobs;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.ExternalNotificationService;
import com.chrono.chrono.services.pms.PmsOperationalHealthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.pms.alerts.enabled", havingValue = "true")
public class PmsOperationalAlertJob {
    private final HotelPropertyRepository propertyRepository;
    private final PmsOperationalHealthService healthService;
    private final ExternalNotificationService notificationService;
    private final EmailService emailService;
    private final String alertEmail;
    private final Map<Long, String> lastFingerprints = new ConcurrentHashMap<>();

    public PmsOperationalAlertJob(HotelPropertyRepository propertyRepository,
                                  PmsOperationalHealthService healthService,
                                  ExternalNotificationService notificationService,
                                  EmailService emailService,
                                  @Value("${app.pms.alerts.email:}") String alertEmail) {
        this.propertyRepository = propertyRepository;
        this.healthService = healthService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.alertEmail = alertEmail == null ? "" : alertEmail.trim();
    }

    @Scheduled(
            fixedDelayString = "${app.pms.alerts.interval-ms:300000}",
            initialDelayString = "${app.pms.alerts.initial-delay-ms:60000}")
    public void dispatchAlerts() {
        for (HotelProperty property : propertyRepository.findAllWithCompany()) {
            PmsOperationalHealthResponse health =
                    healthService.health(property.getCompany(), property.getId());
            String fingerprint = health.alerts().stream()
                    .map(alert -> alert.code() + ":" + alert.severity() + ":" + alert.details())
                    .sorted()
                    .collect(Collectors.joining("|"));
            if (fingerprint.isBlank()) {
                lastFingerprints.remove(property.getId());
                continue;
            }
            if (fingerprint.equals(lastFingerprints.put(property.getId(), fingerprint))) {
                continue;
            }
            String message = formatMessage(property, health);
            notificationService.sendCompanyNotification(property.getCompany(), message);
            if (!alertEmail.isBlank()) {
                emailService.sendOperationalAlert(
                        alertEmail,
                        "Chrono PMS Betriebsalarm: " + property.getName(),
                        message);
            }
        }
    }

    private String formatMessage(HotelProperty property, PmsOperationalHealthResponse health) {
        String alerts = health.alerts().stream()
                .map(alert -> "- [" + alert.severity() + "] " + alert.title() + ": "
                        + alert.details() + " Maßnahme: " + alert.recommendedAction())
                .collect(Collectors.joining(System.lineSeparator()));
        return "Chrono PMS – " + property.getName() + System.lineSeparator()
                + "Gesamtstatus: " + health.status() + System.lineSeparator()
                + alerts;
    }
}
