package com.chrono.chrono.services.banking;

import com.chrono.chrono.config.BankingIntegrationProperties;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentGatewayClientTest {

    private PaymentGatewayClient client;
    private MockRestServiceServer mockServer;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        RestTemplate template = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(template);
        BankingIntegrationProperties properties = new BankingIntegrationProperties();
        properties.getPayments().setEnabled(true);
        properties.getPayments().setMode(BankingIntegrationProperties.Payments.Mode.API);
        properties.getPayments().setEndpoint("https://bank.example.com/pain001");
        properties.getPayments().setApiKey("test-key");
        client = new PaymentGatewayClient(template, properties, new ObjectMapper());
    }

    @Test
    void transmitUsesEndpointAndReturnsReference() {
        mockServer.expect(requestTo("https://bank.example.com/pain001"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("{\"reference\":\"ABC-123\",\"status\":\"SENT\"}", MediaType.APPLICATION_JSON));

        PaymentBatch batch = new PaymentBatch();
        batch.setId(42L);

        PaymentGatewayClient.PaymentSubmissionResult result =
                client.transmit(batch, "<xml/>", "IDEMPOTENT-1");

        assertThat(result.reference()).isEqualTo("ABC-123");
        assertThat(result.providerStatus()).isEqualTo("SENT");
        assertThat(result.deliveryChannel()).isEqualTo("API");
        mockServer.verify();
    }

    @Test
    void transmitExportsPain001ForBankPickup() throws Exception {
        RestTemplate template = new RestTemplate();
        BankingIntegrationProperties properties = new BankingIntegrationProperties();
        properties.getPayments().setEnabled(true);
        properties.getPayments().setMode(BankingIntegrationProperties.Payments.Mode.BANK_EXPORT);
        properties.getPayments().setExportDirectory(tempDir.toString());
        properties.getPayments().setExportChannel("EBICS");
        PaymentGatewayClient exportClient = new PaymentGatewayClient(template, properties, new ObjectMapper());

        PaymentBatch batch = new PaymentBatch();
        batch.setId(84L);

        PaymentGatewayClient.PaymentSubmissionResult result =
                exportClient.transmit(batch, "<xml/>", "EBICS-84");

        assertThat(result.providerStatus()).isEqualTo("EXPORTED");
        assertThat(result.deliveryChannel()).isEqualTo("EBICS");
        assertThat(result.providerArtifactPath()).isNotBlank();
        assertThat(Files.readString(Path.of(result.providerArtifactPath()))).isEqualTo("<xml/>");
        assertThat(Files.exists(Path.of(result.providerArtifactPath()).resolveSibling("batch-84-EBICS-84.json"))).isTrue();
    }
}
