package com.chrono.chrono.services.banking;

import com.chrono.chrono.config.BankingIntegrationProperties;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.exceptions.BankingIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentGatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayClient.class);
    private static final Set<String> FAILURE_STATUSES = Set.of("FAILED", "REJECTED", "ERROR");

    private final RestTemplate restTemplate;
    private final BankingIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentGatewayClient(RestTemplate restTemplate,
                                BankingIntegrationProperties properties,
                                ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.getPayments().isEnabled();
    }

    public PaymentSubmissionResult transmit(PaymentBatch batch, String pain001Xml, String idempotencyKey) {
        if (!isEnabled() || properties.getPayments().getMode() == BankingIntegrationProperties.Payments.Mode.SIMULATED) {
            String reference = resolveReference(batch, idempotencyKey);
            logger.info("Payment gateway disabled. Simulating transmission for batch {} with reference {}", batch.getId(), reference);
            return PaymentSubmissionResult.simulated(reference);
        }

        return switch (properties.getPayments().getMode()) {
            case API -> transmitToApi(batch, pain001Xml, idempotencyKey);
            case BANK_EXPORT -> exportToBankDirectory(batch, pain001Xml, idempotencyKey);
            case SIMULATED -> PaymentSubmissionResult.simulated(resolveReference(batch, idempotencyKey));
        };
    }

    private PaymentSubmissionResult transmitToApi(PaymentBatch batch, String pain001Xml, String idempotencyKey) {
        if (!StringUtils.hasText(properties.getPayments().getEndpoint())) {
            throw new BankingIntegrationException("Payment API mode is enabled but no endpoint is configured");
        }

        try {
            MediaType mediaType = resolveMediaType(properties.getPayments().getContentType());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            if (StringUtils.hasText(properties.getPayments().getApiKey())) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getPayments().getApiKey());
            }
            if (StringUtils.hasText(idempotencyKey)) {
                headers.set("Idempotency-Key", idempotencyKey);
            }

            HttpEntity<?> entity = new HttpEntity<>(buildApiPayload(batch, pain001Xml, idempotencyKey, mediaType), headers);
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
            if (isFailureStatus(status)) {
                throw new BankingIntegrationException("Payment API rejected batch " + batch.getId()
                        + (StringUtils.hasText(message) ? ": " + message : ""));
            }
            return new PaymentSubmissionResult(reference, status, message, "API", null, null);
        } catch (RestClientException ex) {
            logger.error("Failed to transmit payment batch {}: {}", batch.getId(), ex.getMessage());
            throw new BankingIntegrationException("Unable to transmit payment batch: " + ex.getMessage(), ex);
        }
    }

    private PaymentSubmissionResult exportToBankDirectory(PaymentBatch batch, String pain001Xml, String idempotencyKey) {
        if (!StringUtils.hasText(properties.getPayments().getExportDirectory())) {
            throw new BankingIntegrationException("Bank export mode is enabled but no export directory is configured");
        }

        String reference = resolveReference(batch, idempotencyKey);
        String exportChannel = resolveExportChannel();
        Path exportDirectory = resolveExportDirectory(batch);
        String fileBaseName = buildFileBaseName(batch, reference);
        Path xmlPath = exportDirectory.resolve(fileBaseName + ".pain001.xml");
        Path metadataPath = exportDirectory.resolve(fileBaseName + ".json");

        try {
            Files.createDirectories(exportDirectory);
            writeFileAtomically(xmlPath, pain001Xml);
            if (properties.getPayments().isExportMetadata()) {
                writeMetadataFile(metadataPath, batch, reference, exportChannel, xmlPath);
            }

            String message = exportChannel + " bank export written to " + xmlPath.toAbsolutePath();
            logger.info("Exported payment batch {} for {} via {}", batch.getId(), batch.getCompany() != null ? batch.getCompany().getName() : "unknown company", exportChannel);
            return new PaymentSubmissionResult(
                    reference,
                    "EXPORTED",
                    message,
                    exportChannel,
                    xmlPath.toAbsolutePath().toString(),
                    xmlPath.getFileName().toString()
            );
        } catch (IOException ex) {
            logger.error("Failed to export payment batch {}: {}", batch.getId(), ex.getMessage());
            throw new BankingIntegrationException("Unable to export payment batch: " + ex.getMessage(), ex);
        }
    }

    private Object buildApiPayload(PaymentBatch batch, String pain001Xml, String idempotencyKey, MediaType mediaType) {
        if (mediaType.includes(MediaType.APPLICATION_JSON)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("batchId", batch.getId());
            payload.put("companyId", batch.getCompany() != null ? batch.getCompany().getId() : null);
            payload.put("reference", resolveReference(batch, idempotencyKey));
            payload.put("pain001Xml", pain001Xml);
            return payload;
        }
        return pain001Xml;
    }

    private MediaType resolveMediaType(String configuredMediaType) {
        if (!StringUtils.hasText(configuredMediaType)) {
            return MediaType.APPLICATION_XML;
        }
        try {
            return MediaType.parseMediaType(configuredMediaType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_XML;
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

    private boolean isFailureStatus(String status) {
        return StringUtils.hasText(status)
                && FAILURE_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private String resolveExportChannel() {
        String configuredChannel = properties.getPayments().getExportChannel();
        if (!StringUtils.hasText(configuredChannel)) {
            return "BANK_EXPORT";
        }
        return configuredChannel.trim().toUpperCase(Locale.ROOT);
    }

    private Path resolveExportDirectory(PaymentBatch batch) {
        Path basePath = Path.of(properties.getPayments().getExportDirectory()).toAbsolutePath().normalize();
        String companyFolder = batch.getCompany() != null && StringUtils.hasText(batch.getCompany().getName())
                ? sanitizePathToken(batch.getCompany().getName())
                : "company-" + Objects.toString(batch.getCompany() != null ? batch.getCompany().getId() : null, "unknown");
        return basePath.resolve(companyFolder).resolve(LocalDate.now().toString());
    }

    private String buildFileBaseName(PaymentBatch batch, String reference) {
        return "batch-" + Objects.toString(batch.getId(), "unknown")
                + "-" + sanitizePathToken(reference);
    }

    private String sanitizePathToken(String value) {
        String sanitized = value == null ? "n-a" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "n-a" : sanitized;
    }

    private void writeMetadataFile(Path metadataPath,
                                   PaymentBatch batch,
                                   String reference,
                                   String exportChannel,
                                   Path xmlPath) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", batch.getId());
        metadata.put("companyId", batch.getCompany() != null ? batch.getCompany().getId() : null);
        metadata.put("companyName", batch.getCompany() != null ? batch.getCompany().getName() : null);
        metadata.put("bankAccountId", batch.getBankAccount() != null ? batch.getBankAccount().getId() : null);
        metadata.put("debtorIban", batch.getBankAccount() != null ? batch.getBankAccount().getIban() : null);
        metadata.put("reference", reference);
        metadata.put("channel", exportChannel);
        metadata.put("instructionCount", batch.getInstructions() != null ? batch.getInstructions().size() : 0);
        metadata.put("xmlPath", xmlPath.toAbsolutePath().toString());
        metadata.put("createdAt", batch.getCreatedAt() != null ? batch.getCreatedAt().toString() : null);
        String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        writeFileAtomically(metadataPath, content);
    }

    private void writeFileAtomically(Path targetPath, String content) throws IOException {
        Path tempFile = Files.createTempFile(targetPath.getParent(), targetPath.getFileName().toString(), ".tmp");
        Files.writeString(tempFile, content, StandardCharsets.UTF_8);
        try {
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record PaymentGatewayResponse(String reference, String status, String message) {
    }

    public record PaymentSubmissionResult(String reference,
                                          String providerStatus,
                                          String providerMessage,
                                          String deliveryChannel,
                                          String providerArtifactPath,
                                          String providerArtifactName) {
        public static PaymentSubmissionResult simulated(String reference) {
            return new PaymentSubmissionResult(reference, "SIMULATED",
                    "Payment gateway not configured - transmission simulated.", "SIMULATED", null, null);
        }
    }
}
