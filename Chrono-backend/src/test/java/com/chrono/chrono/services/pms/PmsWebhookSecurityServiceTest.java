package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.entities.pms.ChannelConnectionStatus;
import com.chrono.chrono.repositories.pms.ChannelConnectionRepository;
import com.chrono.chrono.repositories.pms.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmsWebhookSecurityServiceTest {
    private static final String SECRET = "sandbox-secret-with-enough-entropy";
    private final ChannelConnectionRepository repository = mock(ChannelConnectionRepository.class);
    private final PmsSecretResolver resolver = mock(PmsSecretResolver.class);
    private final WebhookDeliveryRepository deliveryRepository = mock(WebhookDeliveryRepository.class);
    private final PmsWebhookSecurityService service =
            new PmsWebhookSecurityService(repository, resolver, deliveryRepository, Duration.ofMinutes(5));

    @Test
    void acceptsFreshConstantTimeHmacSignature() throws Exception {
        ChannelConnection connection = connection();
        when(repository.findByWebhookKey("0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.of(connection));
        when(resolver.resolve("env:PMS_TEST_SECRET")).thenReturn(Optional.of(SECRET));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String deliveryId = "delivery-00000001";
        String body = "{\"externalId\":\"abc-1\"}";

        ChannelConnection result = service.verify(
                "0123456789abcdef0123456789abcdef", timestamp, deliveryId,
                "sha256=" + hmac(timestamp + "." + deliveryId + "." + body), body);

        assertThat(result).isSameAs(connection);
    }

    @Test
    void rejectsExpiredOrManipulatedRequests() {
        ChannelConnection connection = connection();
        when(repository.findByWebhookKey("0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.of(connection));
        when(resolver.resolve("env:PMS_TEST_SECRET")).thenReturn(Optional.of(SECRET));

        assertThatThrownBy(() -> service.verify(
                "0123456789abcdef0123456789abcdef",
                String.valueOf(Instant.now().minus(Duration.ofMinutes(6)).getEpochSecond()),
                "delivery-00000002", "sha256=00", "{}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Zeitfenster");

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        assertThatThrownBy(() -> service.verify(
                "0123456789abcdef0123456789abcdef", timestamp, "delivery-00000003", "sha256=00", "{\"changed\":true}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Signatur");
    }

    private ChannelConnection connection() {
        ChannelConnection connection = new ChannelConnection();
        ReflectionTestUtils.setField(connection, "id", 7L);
        connection.setStatus(ChannelConnectionStatus.READY);
        connection.setSecretReference("env:PMS_TEST_SECRET");
        return connection;
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
