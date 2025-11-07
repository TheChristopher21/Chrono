package com.chrono.chrono.services.banking;

import com.chrono.chrono.config.BankingIntegrationProperties;
import com.chrono.chrono.entities.banking.PaymentBatch;
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

import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentGatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final RestTemplate restTemplate;
    private final BankingIntegrationProperties properties;

    public PaymentGatewayClient(RestTemplate restTemplate,
                                BankingIntegrationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getPayments().isEnabled()
                && StringUtils.hasText(properties.getPayments().getEndpoint());
    }

    public PaymentSubmissionResult transmit(PaymentBatch batch, String pain001Xml, String idempotencyKey) {
        if (!isEnabled()) {
            String reference = resolveReference(batch, idempotencyKey);
            logger.info("Payment gateway disabled. Simulating transmission for batch {} with reference {}", batch.getId(), reference);
            return PaymentSubmissionResult.simulated(reference);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            if (StringUtils.hasText(properties.getPayments().getApiKey())) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getPayments().getApiKey());
            }
            if (StringUtils.hasText(idempotencyKey)) {
                headers.set("Idempotency-Key", idempotencyKey);
            }

            HttpEntity<String> entity = new HttpEntity<>(pain001Xml, headers);
            ResponseEntity<PaymentGatewayResponse> response = restTemplate.exchange(
                    properties.getPayments().getEndpoint(),
                    HttpMethod.POST,
                    entity,
                    PaymentGatewayResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BankingIntegrationException("Payment gateway responded with status " + response.getStatusCodeValue());
            }

            PaymentGatewayResponse body = response.getBody();
            String reference = body != null && StringUtils.hasText(body.reference())
                    ? body.reference()
                    : resolveReference(batch, idempotencyKey);
            String status = body != null && StringUtils.hasText(body.status()) ? body.status() : "SENT";
            String message = body != null ? body.message() : null;
            return new PaymentSubmissionResult(reference, status, message);
        } catch (RestClientException ex) {
            logger.error("Failed to transmit payment batch {}: {}", batch.getId(), ex.getMessage());
            throw new BankingIntegrationException("Unable to transmit payment batch: " + ex.getMessage(), ex);
        }
    }

    private String resolveReference(PaymentBatch batch, String providedReference) {
        if (StringUtils.hasText(providedReference)) {
            return providedReference;
        }
        if (batch.getTransmissionReference() != null) {
            return batch.getTransmissionReference();
        }
        return "BATCH-" + Objects.toString(batch.getId(), UUID.randomUUID().toString());
    }

    public record PaymentGatewayResponse(String reference, String status, String message) {
    }

    public record PaymentSubmissionResult(String reference, String providerStatus, String providerMessage) {
        public static PaymentSubmissionResult simulated(String reference) {
            return new PaymentSubmissionResult(reference, "SIMULATED",
                    "Payment gateway not configured - transmission simulated.");
        }
    }
}
