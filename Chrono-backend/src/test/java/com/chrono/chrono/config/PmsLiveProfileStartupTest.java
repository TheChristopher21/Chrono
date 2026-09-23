package com.chrono.chrono.config;

import com.chrono.chrono.ChronoApplication;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import com.chrono.chrono.services.pms.PmsSetupService;
import com.chrono.chrono.services.pms.PmsReportingService;
import com.chrono.chrono.services.pms.PmsRevenuePlanningService;
import com.chrono.chrono.services.pms.PmsPaymentGateway;
import com.chrono.chrono.services.pms.SimulatedPmsPaymentGateway;
import com.chrono.chrono.services.UserPermissionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("live")
@Import(PmsLiveProfileStartupTest.DemoFixture.class)
@SpringBootTest(
        classes = ChronoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "spring.datasource.url=jdbc:h2:mem:chrono_live_startup;MODE=MySQL;"
                        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                // H2 reports MySQL LONGTEXT as VARCHAR. The separate production
                // configuration test asserts ddl-auto=validate for the real MySQL runtime.
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.baseline-version=14",
                "spring.task.scheduling.enabled=false",
                "app.initialize.admin=false",
                "app.pms.test-account.enabled=false",
                "app.pms.demo-data.enabled=false",
                "app.pms.payments.simulated.enabled=false",
                "app.pms.outbox.enabled=false",
                "app.pms.alerts.enabled=false",
                "app.security.allowed-origins=https://chrono-logisch.ch",
                "app.backup.restore-test.enabled=false",
                "jwt.secret=live-startup-test-jwt-secret-32-characters",
                "nfc.agent.token=live-startup-test-nfc-token-32-characters",
                "report.ics-feed.token=live-startup-test-report-token-32-chars",
                "app.pms.document-hmac-key=live-startup-document-hmac-key-32-chars",
                "app.pms.audit-hmac-key=live-startup-audit-hmac-key-32-characters",
                "spring.mail.host=localhost",
                "spring.mail.port=2525",
                "spring.mail.password=local-test-only"
        }
)
class PmsLiveProfileStartupTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PmsReportingService reporting;

    @Autowired
    private PmsRevenuePlanningService revenuePlanning;

    @Autowired
    private Environment environment;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void startsTheLiveApplicationWiringAgainstAFreshMigratedDatabase() {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("app.production", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.pms.test-account.enabled", Boolean.class)).isFalse();
        assertThat(applicationPort).isPositive();
        assertThat(managementPort).isPositive().isNotEqualTo(applicationPort);
        assertThat(restTemplate.getForEntity(
                "http://127.0.0.1:" + managementPort + "/actuator/health/readiness",
                String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_properties WHERE code='DEMO'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_reservations", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_payments", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_invoice_lines", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_folio_items WHERE tax_rate IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_folio_items WHERE type='ROOM' AND tax_rate<>3.80", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_folio_items WHERE type='SERVICE' AND tax_rate=8.10", Long.class)).isPositive();
        var fixtureUser = users.findByUsernameWithPermissionContext("migrated-startup-test").orElseThrow();
        Long propertyId = jdbc.queryForObject("SELECT id FROM pms_properties WHERE code='DEMO'", Long.class);
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Zurich"));
        assertThat(reporting.performance(fixtureUser.getCompany(), propertyId, today, today.plusDays(30)).roomRevenue()).isPositive();
        var planning = revenuePlanning.report(fixtureUser.getUsername(), propertyId, today, today.plusDays(30), null);
        assertThat(planning.summary().netRoomRevenue()).isPositive();
        assertThat(planning.days()).allSatisfy(day -> assertThat(day.unknownRevenueRoomNights()).isZero());
        // Querying an unsupported MySQL/H2 ENUM value previously aborted this exact startup flow.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_payments WHERE status='PENDING'", Long.class))
                .isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DemoFixture {
        interface OrderedFixtureRunner extends CommandLineRunner, Ordered {
            @Override
            default int getOrder() { return 100; }
        }

        @Bean
        OrderedFixtureRunner fixtureAccount(UserRepository users, RoleRepository roles, CompanyRepository companies,
                                         PasswordEncoder encoder, UserPermissionService permissions) {
            return args -> {
                // Create fixture data explicitly without enabling production bootstrap switches.
                PmsTestAccountInitializer fixture = new PmsTestAccountInitializer(users, roles, companies, encoder, permissions);
                ReflectionTestUtils.setField(fixture, "enabled", true);
                ReflectionTestUtils.setField(fixture, "username", "migrated-startup-test");
                ReflectionTestUtils.setField(fixture, "password", "isolated-startup-fixture-only");
                fixture.run(args);
            };
        }

        // The test exercises real posting/services against Flyway tables, substituting only the card provider.
        @Bean
        PmsPaymentGateway fixturePaymentGateway() {
            return new SimulatedPmsPaymentGateway();
        }

        // Explicit test fixture only: the application initializer remains restricted to the local profile.
        @Bean
        PmsDemoDataInitializer migratedSchemaDemoFixture(
                UserRepository users, HotelPropertyRepository properties, com.chrono.chrono.repositories.pms.PmsPropertyGrantRepository grants, PmsSetupService setup,
                PmsOperationsService operations, PmsAdvancedService advanced) {
            return new PmsDemoDataInitializer(users, properties, grants, setup, operations, advanced,
                    "migrated-startup-test");
        }
    }
}
