package com.chrono.chrono.services.banking;

import com.chrono.chrono.config.BankingIntegrationProperties;
import com.chrono.chrono.entities.banking.SignatureStatus;
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

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DigitalSignatureProviderClient {

    private static final Logger logger = LoggerFactory.getLogger(DigitalSignatureProviderClient.class);

    private final RestTemplate restTemplate;
    private final BankingIntegrationProperties properties;

    public DigitalSignatureProviderClient(RestTemplate restTemplate,
                                          BankingIntegrationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getSignatures().isEnabled()
                && StringUtils.hasText(properties.getSignatures().getBaseUrl());
    }

    public SignatureCreationResult createSignatureRequest(String documentType, String path, String email) {
        if (!isEnabled()) {
            String reference = "SIM-" + UUID.randomUUID();
            logger.info("Signature provider disabled. Simulating request {}", reference);
            return SignatureCreationResult.simulated(reference);
        }

        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "documentType", documentType,
                    "documentPath", path,
                    "signerEmail", email
            );
            ResponseEntity<SignatureProviderResponse> response = restTemplate.exchange(
                    normalizeBaseUrl(properties.getSignatures().getBaseUrl()) + "/requests",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    SignatureProviderResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BankingIntegrationException("Signature provider responded with status " + response.getStatusCodeValue());
            }

            SignatureProviderResponse body = response.getBody();
            if (body == null || !StringUtils.hasText(body.reference())) {
                throw new BankingIntegrationException("Signature provider returned an empty reference");
            }
            SignatureStatus status = mapStatus(body.status());
            return new SignatureCreationResult(status, body.reference(), body.signingUrl(), body.message());
        } catch (RestClientException ex) {
            logger.error("Failed to create signature request: {}", ex.getMessage());
            throw new BankingIntegrationException("Unable to create signature request: " + ex.getMessage(), ex);
        }
    }

    public SignatureStatusUpdate fetchStatus(String providerReference) {
        if (!isEnabled()) {
            return SignatureStatusUpdate.simulated(SignatureStatus.IN_PROGRESS);
        }
        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_JSON);
            ResponseEntity<SignatureProviderResponse> response = restTemplate.exchange(
                    normalizeBaseUrl(properties.getSignatures().getBaseUrl()) + "/requests/" + providerReference,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    SignatureProviderResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BankingIntegrationException("Signature provider status request failed with status " + response.getStatusCodeValue());
            }

            SignatureProviderResponse body = response.getBody();
            if (body == null) {
                throw new BankingIntegrationException("Signature provider returned an empty response");
            }
            SignatureStatus status = mapStatus(body.status());
            return new SignatureStatusUpdate(status, body.message(), body.signingUrl());
        } catch (RestClientException ex) {
            logger.error("Failed to fetch signature status for {}: {}", providerReference, ex.getMessage());
            throw new BankingIntegrationException("Unable to fetch signature status: " + ex.getMessage(), ex);
        }
    }

    public SignatureStatusUpdate finalizeSignature(String providerReference) {
        if (!isEnabled()) {
            return SignatureStatusUpdate.simulated(SignatureStatus.COMPLETED);
        }
        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_JSON);
            ResponseEntity<SignatureProviderResponse> response = restTemplate.exchange(
                    normalizeBaseUrl(properties.getSignatures().getBaseUrl()) + "/requests/" + providerReference + "/complete",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(), headers),
                    SignatureProviderResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BankingIntegrationException("Signature completion failed with status " + response.getStatusCodeValue());
            }

            SignatureProviderResponse body = response.getBody();
            SignatureStatus status = mapStatus(body != null ? body.status() : null);
            String message = body != null ? body.message() : null;
            String signingUrl = body != null ? body.signingUrl() : null;
            return new SignatureStatusUpdate(status, message, signingUrl);
        } catch (RestClientException ex) {
            logger.error("Failed to finalize signature {}: {}", providerReference, ex.getMessage());
            throw new BankingIntegrationException("Unable to finalize signature: " + ex.getMessage(), ex);
        }
    }

    private HttpHeaders buildHeaders(MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        if (StringUtils.hasText(properties.getSignatures().getApiKey())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSignatures().getApiKey());
        }
        return headers;
    }

    private SignatureStatus mapStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return SignatureStatus.IN_PROGRESS;
        }
        try {
            return SignatureStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SignatureStatus.IN_PROGRESS;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public record SignatureProviderResponse(String reference, String status, String signingUrl, String message) {
    }

    public record SignatureCreationResult(SignatureStatus status, String providerReference, String signingUrl, String providerMessage) {
        public static SignatureCreationResult simulated(String reference) {
            return new SignatureCreationResult(SignatureStatus.IN_PROGRESS, reference, null,
                    "Signature provider not configured - request simulated.");
        }
    }

    public record SignatureStatusUpdate(SignatureStatus status, String providerMessage, String signingUrl) {
        public static SignatureStatusUpdate simulated(SignatureStatus status) {
            return new SignatureStatusUpdate(status,
                    "Signature provider not configured - status simulated.", null);
        }
    }
}
