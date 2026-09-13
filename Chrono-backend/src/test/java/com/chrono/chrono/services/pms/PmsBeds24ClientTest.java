package com.chrono.chrono.services.pms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsBeds24ClientTest {
    private static final String PROPERTY = "{\"success\":true,\"data\":[{\"id\":41,\"currency\":\"CHF\",\"roomTypes\":[{\"id\":71}]}]}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokens = new AtomicInteger();
    private HttpServer server;
    private PmsBeds24Client client;
    private Function<Request, Reply> respond;
    private record Request(String method, String path, String query, String token, String refreshToken, String body) {}
    private record Reply(int code, String body, String waitSeconds) { Reply(int code, String body) { this(code, body, null); } }

    @BeforeEach void setup() throws Exception {
        respond = request -> new Reply(200, PROPERTY);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/", exchange -> {
            var request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("token"), exchange.getRequestHeaders().getFirst("refreshToken"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            var reply = request.path().endsWith("authentication/token")
                    ? new Reply(200, "{\"token\":\"access-" + tokens.incrementAndGet() + "\",\"expiresIn\":3600}") : respond.apply(request);
            if (reply.waitSeconds() != null) exchange.getResponseHeaders().set("X-FiveMinCreditLimit-ResetsIn", reply.waitSeconds());
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.code(), bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        var secrets = mock(PmsSecretResolver.class);
        when(secrets.resolve("env:HOTEL_BEDS24")).thenReturn(Optional.of("test-refresh-only"));
        client = new PmsBeds24Client(mapper, secrets, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/"), HttpClient.newHttpClient());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void usesDocumentedRefreshAndAccessHeadersAndActualCalendarContract() throws Exception {
        assertThat(client.verifyProperty("env:HOTEL_BEDS24", 41L, "CHF")).containsExactly(71L);
        var payload = mapper.readTree("[{\"roomId\":71,\"calendar\":[{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"price1\":120.25,\"numAvail\":3,\"override\":\"noCheckIn\"}]}]");
        respond = request -> new Reply(201, "[{\"success\":true}]");
        client.publishCalendar("env:HOTEL_BEDS24", payload);
        assertThat(tokens.get()).isEqualTo(1);
        assertThat(requests.get(0).refreshToken()).isEqualTo("test-refresh-only");
        assertThat(requests.get(1).query()).contains("id=41", "includeAllRooms=true");
        var sent = requests.get(2);
        assertThat(sent.path()).isEqualTo("/v2/inventory/rooms/calendar");
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.token()).isEqualTo("access-1");
        assertThat(sent.refreshToken()).isNull();
        assertThat(mapper.readTree(sent.body())).isEqualTo(payload);
    }

    @Test void http201WithPartialFailureIsNeverTreatedAsPublished() throws Exception {
        respond = request -> new Reply(201, "[{\"success\":true},{\"success\":false,\"errors\":[\"private provider detail\"]}]");
        var payload = mapper.readTree("[{\"roomId\":71,\"calendar\":[]},{\"roomId\":72,\"calendar\":[]}]");
        assertThatThrownBy(() -> client.publishCalendar("env:HOTEL_BEDS24", payload))
                .isInstanceOf(PmsBeds24Client.ProviderFailure.class).hasMessageContaining("mindestens einen Kalenderteil").hasMessageNotContaining("private provider detail");
    }

    @Test void expiredAccessTokenIsRefreshedOnceAndThenRetried() throws Exception {
        respond = request -> "access-1".equals(request.token()) ? new Reply(401, "{}") : new Reply(200, PROPERTY);
        assertThat(client.verifyProperty("env:HOTEL_BEDS24", 41L, "CHF")).containsExactly(71L);
        assertThat(tokens.get()).isEqualTo(2);
        respond = request -> new Reply(401, "{}");
        assertThatThrownBy(() -> client.verifyProperty("env:HOTEL_BEDS24", 41L, "CHF")).hasMessageContaining("HTTP 401");
        assertThat(tokens.get()).isEqualTo(3);
    }

    @Test void rateLimitUsesBoundedProviderRetryDelayWithoutLeakingPayload() {
        respond = request -> new Reply(429, "{\"secret\":\"test-refresh-only\"}", "120");
        assertThatThrownBy(() -> client.verifyProperty("env:HOTEL_BEDS24", 41L, "CHF"))
                .isInstanceOfSatisfying(PmsBeds24Client.ProviderFailure.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(120))
                .hasMessageNotContaining("test-refresh-only");
    }

    @Test void bookingPaginationKeepsPropertyAndStatusFiltersAndNeverFollowsRemoteNextLinks() throws Exception {
        respond = request -> new Reply(200, request.query().contains("page=1")
                ? "{\"success\":true,\"pages\":{\"nextPageExists\":true,\"nextPageLink\":\"https://invalid.example/collect\"},\"data\":[{\"id\":1,\"propertyId\":41,\"roomId\":71,\"status\":\"confirmed\",\"arrival\":\"2026-10-01\",\"departure\":\"2026-10-03\",\"numAdult\":2,\"numChild\":1,\"price\":250.25,\"cardNumber\":\"not retained\"}]}"
                : "{\"success\":true,\"pages\":{\"nextPageExists\":false},\"data\":[]}");
        var rows = client.bookings("env:HOTEL_BEDS24", 41L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).price()).isEqualByComparingTo("250.25");
        assertThat(mapper.writeValueAsString(rows)).doesNotContain("cardNumber", "not retained");
        assertThat(requests.subList(1, requests.size())).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v2/bookings");
            assertThat(request.query()).contains("propertyId=41", "status=confirmed", "status=new", "status=cancelled");
        });
        assertThat(requests.get(2).query()).contains("page=2");
    }

    @Test void anotherHotelOrCurrencyCannotBeAcceptedAsMatchingProviderConfiguration() {
        assertThatThrownBy(() -> client.verifyProperty("env:HOTEL_BEDS24", 41L, "EUR")).hasMessageContaining("Hotelwährung");
        assertThatThrownBy(() -> client.verifyProperty("env:HOTEL_BEDS24", 42L, "CHF")).hasMessageContaining("nicht zugänglich");
        respond = request -> new Reply(200, "{\"data\":[{\"id\":2,\"propertyId\":42}]}");
        assertThatThrownBy(() -> client.bookings("env:HOTEL_BEDS24", 41L, LocalDate.now(), LocalDate.now().plusDays(2))).hasMessageContaining("anderen Hotels");
    }

    @Test void productionEndpointAcceptsOnlyDocumentedHttpsHosts() {
        assertThat(PmsBeds24Client.validateBase("https://beds24.com/api/v2").toString()).isEqualTo("https://beds24.com/api/v2/");
        assertThatThrownBy(() -> PmsBeds24Client.validateBase("http://127.0.0.1/v2/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PmsBeds24Client.validateBase("https://beds24.com.evil.example/api/v2/")).isInstanceOf(IllegalArgumentException.class);
    }
}
