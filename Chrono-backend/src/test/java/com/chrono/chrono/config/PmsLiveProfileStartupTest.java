package com.chrono.chrono.config;

import com.chrono.chrono.ChronoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("live")
@SpringBootTest(
        classes = ChronoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
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
    private Environment environment;

    @Test
    void startsTheLiveApplicationWiringAgainstAFreshMigratedDatabase() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class))
                .isTrue();
    }
}
