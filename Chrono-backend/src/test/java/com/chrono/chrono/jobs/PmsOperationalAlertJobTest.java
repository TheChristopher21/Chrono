package com.chrono.chrono.jobs;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.ExternalNotificationService;
import com.chrono.chrono.services.pms.PmsOperationalHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmsOperationalAlertJobTest {

    @Test
    void sendsChangedAlertOnlyOnceUntilSystemRecovers() {
        HotelPropertyRepository propertyRepository = mock(HotelPropertyRepository.class);
        PmsOperationalHealthService healthService = mock(PmsOperationalHealthService.class);
        ExternalNotificationService notificationService = mock(ExternalNotificationService.class);
        EmailService emailService = mock(EmailService.class);
        Company company = new Company("Chrono Hotel AG");
        company.setId(3L);
        HotelProperty property = new HotelProperty();
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        property.setName("Chrono Zürich");
        property.setEmail("betrieb@example.com");
        when(propertyRepository.findAllWithCompany()).thenReturn(List.of(property));
        when(healthService.health(company, 5L)).thenReturn(criticalHealth());
        PmsOperationalAlertJob job = new PmsOperationalAlertJob(
                propertyRepository, healthService, notificationService, emailService);

        job.dispatchAlerts();
        job.dispatchAlerts();

        verify(notificationService, times(1))
                .sendCompanyNotification(eq(company), contains("Datenbanksicherung"));
        verify(emailService, times(1)).sendOperationalAlert(
                eq("betrieb@example.com"),
                eq("Chrono PMS Betriebsalarm: Chrono Zürich"),
                contains("Gesamtstatus: CRITICAL"));
    }

    private PmsOperationalHealthResponse criticalHealth() {
        return new PmsOperationalHealthResponse(
                5L,
                HealthStatus.CRITICAL,
                LocalDateTime.now(),
                0,
                0,
                0,
                List.of(),
                List.of(new PmsOperationalHealthResponse.OperationalAlert(
                        "backup.failed",
                        HealthStatus.CRITICAL,
                        "Datenbanksicherung",
                        "Kein gültiges Backup vorhanden.",
                        "Backup-Konfiguration prüfen.")));
    }
}
