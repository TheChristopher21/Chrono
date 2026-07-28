package com.chrono.chrono.services.pms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmsProviderGatewayTransportTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsSignedIdempotentEnvelope() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/events", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Chrono-Timestamp"));
            signature.set(exchange.getRequestHeaders().getFirst("X-Chrono-Signature"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        String secret = "0123456789abcdef0123456789abcdef";
        ObjectMapper objectMapper = new ObjectMapper();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/events");
        PmsProviderGatewayTransport transport = new PmsProviderGatewayTransport(
                objectMapper,
                endpoint,
                secret,
                Duration.ofSeconds(3),
                HttpClient.newHttpClient());

        transport.deliver(new PmsOutboxMessage(
                41L, 7L, "reservation.created", "reservation", "99",
                "{\"confirmationCode\":\"CHR-99\"}", 1));

        JsonNode envelope = objectMapper.readTree(body.get());
        assertThat(envelope.path("eventId").asLong()).isEqualTo(41L);
        assertThat(envelope.path("payload").path("confirmationCode").asText()).isEqualTo("CHR-99");
        assertThat(idempotencyKey.get()).isEqualTo("chrono-pms-41");
        assertThat(signature.get()).isEqualTo(
                "v1=" + PmsProviderGatewayTransport.sign(
                        timestamp.get(),
                        body.get().getBytes(StandardCharsets.UTF_8),
                        secret.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void productionRejectsPlainHttpEndpoint() {
        assertThatThrownBy(() -> PmsProviderGatewayTransport.parseEndpoint(
                "http://provider.example/events", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PMS provider gateway endpoint must use HTTPS");
    }
}
