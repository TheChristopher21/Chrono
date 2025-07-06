package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.VacationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ExternalNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalNotificationService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendVacationNotification(VacationRequest vr, String message) {
        if (vr == null || vr.getUser() == null) return;
        Company company = vr.getUser().getCompany();
        if (company == null) return;
        if (Boolean.FALSE.equals(company.getNotifyVacation())) return;

        if (company.getSlackWebhookUrl() != null && !company.getSlackWebhookUrl().isBlank()) {
            sendSimpleMessage(company.getSlackWebhookUrl(), message);
        }
        if (company.getTeamsWebhookUrl() != null && !company.getTeamsWebhookUrl().isBlank()) {
            sendSimpleMessage(company.getTeamsWebhookUrl(), message);
        }
    }

    private void sendSimpleMessage(String url, String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            logger.warn("Failed to send webhook notification: {}", e.getMessage());
        }
    }
}
