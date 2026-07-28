package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.entities.pms.ChannelConnectionStatus;
import com.chrono.chrono.repositories.pms.ChannelConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
    private final PmsWebhookSecurityService service =
            new PmsWebhookSecurityService(repository, resolver, Duration.ofMinutes(5));

    @Test
    void acceptsFreshConstantTimeHmacSignature() throws Exception {
        ChannelConnection connection = connection();
        when(repository.findByProperty_CodeIgnoreCaseAndProviderCodeIgnoreCase("ZRH", "TEST"))
                .thenReturn(Optional.of(connection));
        when(resolver.resolve("env:PMS_TEST_SECRET")).thenReturn(Optional.of(SECRET));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"externalId\":\"abc-1\"}";

        ChannelConnection result = service.verify(
                "ZRH", "TEST", timestamp, "sha256=" + hmac(timestamp + "." + body), body);

        assertThat(result).isSameAs(connection);
    }

    @Test
    void rejectsExpiredOrManipulatedRequests() {
        ChannelConnection connection = connection();
        when(repository.findByProperty_CodeIgnoreCaseAndProviderCodeIgnoreCase("ZRH", "TEST"))
                .thenReturn(Optional.of(connection));
        when(resolver.resolve("env:PMS_TEST_SECRET")).thenReturn(Optional.of(SECRET));

        assertThatThrownBy(() -> service.verify(
                "ZRH", "TEST",
                String.valueOf(Instant.now().minus(Duration.ofMinutes(6)).getEpochSecond()),
                "sha256=00", "{}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Zeitfenster");

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        assertThatThrownBy(() -> service.verify(
                "ZRH", "TEST", timestamp, "sha256=00", "{\"changed\":true}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Signatur");
    }

    private ChannelConnection connection() {
        ChannelConnection connection = new ChannelConnection();
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
