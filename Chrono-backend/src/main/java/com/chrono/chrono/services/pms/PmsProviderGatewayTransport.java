package com.chrono.chrono.services.pms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Delivers the transactional PMS outbox to a separately certified provider
 * gateway. The stable event id is sent as the idempotency key and every body
 * is authenticated with an HMAC signature.
 */
@Component
@ConditionalOnProperty(name = "app.pms.provider-gateway.enabled", havingValue = "true")
public class PmsProviderGatewayTransport implements PmsOutboxTransport {

    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final byte[] secret;
    private final Duration timeout;
    private final HttpClient httpClient;

    public PmsProviderGatewayTransport(
            ObjectMapper objectMapper,
            @Value("${app.pms.provider-gateway.endpoint}") String endpoint,
            @Value("${app.pms.provider-gateway.secret}") String secret,
            @Value("${app.pms.provider-gateway.timeout:PT10S}") Duration timeout,
            @Value("${app.production:false}") boolean production) {
        this(
                objectMapper,
                parseEndpoint(endpoint, production),
                requireSecret(secret),
                timeout,
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    PmsProviderGatewayTransport(
            ObjectMapper objectMapper,
            URI endpoint,
            String secret,
            Duration timeout,
            HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.secret = requireSecret(secret).getBytes(StandardCharsets.UTF_8);
        this.timeout = timeout;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(PmsOutboxMessage message) {
        return true;
    }

    @Override
    public void deliver(PmsOutboxMessage message) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        byte[] body = objectMapper.writeValueAsBytes(envelope(message));
        String signature = "v1=" + sign(timestamp, body, secret);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "chrono-pms-" + message.id())
                .header("X-Chrono-Event-Id", String.valueOf(message.id()))
                .header("X-Chrono-Timestamp", timestamp)
                .header("X-Chrono-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("PMS provider gateway returned HTTP " + response.statusCode());
        }
    }

    private ObjectNode envelope(PmsOutboxMessage message) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("eventId", message.id());
        envelope.put("propertyId", message.propertyId());
        envelope.put("eventType", message.eventType());
        envelope.put("aggregateType", message.aggregateType());
        envelope.put("aggregateId", message.aggregateId());
        envelope.put("attemptNumber", message.attemptNumber());
        envelope.set("payload", payloadNode(message.payload()));
        return envelope;
    }

    private JsonNode payloadNode(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(payload);
        }
    }

    static URI parseEndpoint(String endpoint, boolean production) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("PMS provider gateway endpoint is required");
        }
        URI uri = URI.create(endpoint.trim());
        if (uri.getHost() == null || (!"https".equalsIgnoreCase(uri.getScheme())
                && (production || !"http".equalsIgnoreCase(uri.getScheme())))) {
            throw new IllegalStateException("PMS provider gateway endpoint must use HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("PMS provider gateway endpoint must not contain credentials or fragments");
        }
        return uri;
    }

    private static String requireSecret(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("PMS provider gateway secret must contain at least 32 characters");
        }
        return secret;
    }

    static String sign(String timestamp, byte[] body, byte[] secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
