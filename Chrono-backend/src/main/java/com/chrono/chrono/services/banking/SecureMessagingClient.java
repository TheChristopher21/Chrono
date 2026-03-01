package com.chrono.chrono.services.banking;

import com.chrono.chrono.config.BankingIntegrationProperties;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.exceptions.BankingIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class SecureMessagingClient {

    private static final Logger logger = LoggerFactory.getLogger(SecureMessagingClient.class);

    private final RestTemplate restTemplate;
    private final BankingIntegrationProperties properties;

    public SecureMessagingClient(RestTemplate restTemplate,
                                 BankingIntegrationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getMessages().isEnabled()
                && StringUtils.hasText(properties.getMessages().getEndpoint());
    }

    public SecureMessageResult sendSecureMessage(Company company, String recipient, String subject, String body, String transport) {
        if (!isEnabled()) {
            logger.info("Secure messaging provider disabled. Simulating delivery to {}", recipient);
            return SecureMessageResult.simulated();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.getMessages().getApiKey())) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getMessages().getApiKey());
            }

            Map<String, Object> payload = Map.of(
                    "company", company != null ? company.getName() : null,
                    "recipient", recipient,
                    "subject", subject,
                    "body", body,
                    "transport", transport
            );

            ResponseEntity<SecureMessageResponse> response = restTemplate.exchange(
                    properties.getMessages().getEndpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    SecureMessageResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BankingIntegrationException("Secure messaging provider returned status " + response.getStatusCodeValue());
            }

            SecureMessageResponse bodyResponse = response.getBody();
            if (bodyResponse == null) {
                return new SecureMessageResult(true, null, "DELIVERED", null);
            }
            boolean delivered = bodyResponse.delivered() != null ? bodyResponse.delivered() : true;
            return new SecureMessageResult(delivered, bodyResponse.reference(), bodyResponse.status(), bodyResponse.message());
        } catch (RestClientException ex) {
            logger.error("Failed to deliver secure message to {}: {}", recipient, ex.getMessage());
            throw new BankingIntegrationException("Unable to send secure message: " + ex.getMessage(), ex);
        }
    }

    public record SecureMessageResponse(Boolean delivered, String reference, String status, String message) {
    }

    public record SecureMessageResult(boolean delivered, String providerReference, String providerStatus, String providerMessage) {
        public static SecureMessageResult simulated() {
            return new SecureMessageResult(false, null, "SIMULATED",
                    "Secure messaging provider not configured - delivery pending.");
        }
    }
}
