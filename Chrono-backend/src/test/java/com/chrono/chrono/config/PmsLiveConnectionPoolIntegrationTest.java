package com.chrono.chrono.config;

import com.chrono.chrono.controller.pms.PmsLiveUpdateController;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import com.chrono.chrono.services.CustomUserDetailsService;
import com.chrono.chrono.services.pms.PmsLiveUpdateService;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.chrono.chrono.utils.JwtAuthenticationFilter;
import com.chrono.chrono.utils.JwtUtil;
import com.chrono.chrono.utils.PasswordEncoderConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP streams and a real two-connection JDBC pool; no external provider or database. */
@SpringBootTest(classes = PmsLiveConnectionPoolIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pms_live_pool;DB_CLOSE_DELAY=-1",
                "spring.datasource.hikari.maximum-pool-size=2",
                "spring.datasource.hikari.minimum-idle=2",
                "spring.datasource.hikari.connection-timeout=500",
                "spring.jpa.open-in-view=true",
                "spring.data.jpa.repositories.enabled=false",
                "spring.flyway.enabled=false",
                "management.server.port=0"
        })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PmsLiveConnectionPoolIntegrationTest {
    @Autowired HikariDataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired Probe probe;
    @Autowired JwtUtil jwt;
    @LocalServerPort int port;

    @Test
    void twelveAuthorizedStreamsReleasePoolAndThirteenthReturns429WithoutLosingAuthentication() throws Exception {
        String token = jwt.generateToken(User.withUsername("pool-staff").password("unused").roles("USER").build());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        List<InputStream> streams = new ArrayList<>();
        try {
            // More simultaneous streams than the pool capacity fails on the old global OSIV mapping.
            for (int index = 0; index < 12; index++) {
                HttpResponse<InputStream> response = client.send(request("/api/pms/properties/9/live", token),
                        HttpResponse.BodyHandlers.ofInputStream());
                streams.add(response.body());
                assertThat(response.statusCode()).isEqualTo(200);
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
                assertThat(reader.readLine()).isEqualTo("event:ready");
            }
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero());
            assertThat(dataSource.getHikariPoolMXBean().getTotalConnections()).isEqualTo(2);

            HttpResponse<String> limited = client.send(request("/api/pms/properties/9/live", token),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.body()).isEmpty();
            assertThat(limited.headers().firstValue("Retry-After")).contains("15");
            HttpResponse<String> currentUser = client.send(request("/api/auth/me", token), HttpResponse.BodyHandlers.ofString());
            assertThat(currentUser.statusCode()).isEqualTo(200);
            assertThat(currentUser.body()).isEqualTo("pool-staff:1");

            // The ordinary request still has OSIV and can query while all twelve streams stay open.
            HttpResponse<String> ordinary = client.send(request("/pool-probe", token), HttpResponse.BodyHandlers.ofString());
            assertThat(ordinary.statusCode()).isEqualTo(200);
            assertThat(ordinary.body()).isEqualTo("bound:1");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero());
        } finally {
            for (InputStream stream : streams) stream.close();
        }
    }

    @Test
    void frameworkControlConfirmsNontransactionalReadRetainsItsConnectionUntilAsyncCompletion() {
        OpenEntityManagerInViewInterceptor original = new OpenEntityManagerInViewInterceptor();
        original.setEntityManagerFactory(factory);
        var request = new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());
        original.preHandle(request);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
        try {
            assertThat(probe.query()).isEqualTo(1);
            original.afterConcurrentHandlingStarted(request);
            assertThat(TransactionSynchronizationManager.hasResource(factory)).isFalse();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
        } finally {
            if (!TransactionSynchronizationManager.hasResource(factory)) {
                TransactionSynchronizationManager.bindResource(factory, holder);
            }
            original.afterCompletion(request, null);
        }
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private HttpRequest request(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token);
        if (path.endsWith("/live")) request.header("Accept", "text/event-stream");
        return request.GET().build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = PoolMarker.class)
    @Import({PmsOpenEntityManagerInViewConfiguration.class, PmsLiveUpdateService.class,
            PmsLiveUpdateController.class, SecurityConfig.class, JwtAuthenticationFilter.class,
            JwtUtil.class, PasswordEncoderConfig.class, Probe.class})
    static class Application {
        @Bean com.chrono.chrono.repositories.UserRepository userRepository() {
            return mock(com.chrono.chrono.repositories.UserRepository.class);
        }
        @Bean com.chrono.chrono.repositories.pms.PmsUserDirectoryRepository userDirectory() {
            return mock(com.chrono.chrono.repositories.pms.PmsUserDirectoryRepository.class);
        }
        @Bean CustomUserDetailsService users() {
            CustomUserDetailsService users = mock(CustomUserDetailsService.class);
            when(users.loadUserByUsername("pool-staff")).thenReturn(
                    User.withUsername("pool-staff").password("unused").roles("USER").build());
            return users;
        }
        @Bean PmsPropertyAccessService access(Probe probe) {
            PmsPropertyAccessService access = mock(PmsPropertyAccessService.class);
            when(access.access("pool-staff")).thenReturn(new PmsPropertyAccessService.Access(
                    1L, 4L, false, Map.of(9L, Map.of("HOUSEKEEPING", "VIEW"))));
            doAnswer(call -> { probe.query(); return null; }).when(access).require(any(), eq(9L), isNull(), eq(false));
            return access;
        }
        @Bean PmsAuditEventRepository audit(Probe probe) {
            PmsAuditEventRepository audit = mock(PmsAuditEventRepository.class);
            // Real JPA terminal queries reproduce the connection lifecycle of the production repositories.
            when(audit.findMaximumSequence(9L)).thenAnswer(call -> (long) probe.query());
            when(audit.liveSequences(eq(4L), anyCollection())).thenAnswer(call -> {
                probe.query(); return List.<Object[]>of(new Object[]{9L, 1L});
            });
            return audit;
        }
    }

    @RestController
    static class Probe {
        @PersistenceContext EntityManager entityManager;
        @Autowired EntityManagerFactory factory;
        int query() { return ((Number) entityManager.createNativeQuery("select 1").getSingleResult()).intValue(); }
        @GetMapping("/pool-probe") String ordinary() {
            return (TransactionSynchronizationManager.hasResource(factory) ? "bound:" : "unbound:") + query();
        }
        @GetMapping("/api/auth/me") String currentUser(Principal principal) {
            if (principal == null) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
            return principal.getName() + ":" + query();
        }
    }

    @Entity(name = "PmsLivePoolMarker")
    static class PoolMarker {
        @Id Long id;
    }
}
