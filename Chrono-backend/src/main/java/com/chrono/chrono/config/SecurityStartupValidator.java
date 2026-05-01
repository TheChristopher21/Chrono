package com.chrono.chrono.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityStartupValidator implements ApplicationRunner {

    private static final Set<String> KNOWN_UNSAFE_VALUES = Set.of(
            "admin",
            "demo",
            "SUPER-SECRET-AGENT-TOKEN",
            "MeinSuperGeheimerSchluessel1234567890"
    );

    @Value("${app.production:false}")
    private boolean production;

    @Value("${app.demo-login.enabled:false}")
    private boolean demoLoginEnabled;

    @Value("${app.initialize.admin:false}")
    private boolean initializeAdmin;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${nfc.agent.token:}")
    private String nfcAgentToken;

    @Value("${nfc.agent.allow-localhost-legacy:false}")
    private boolean allowLocalhostLegacy;

    @Value("${report.ics-feed.public-without-token:false}")
    private boolean publicIcsFeedWithoutToken;

    @Value("${report.ics-feed.token:}")
    private String reportIcsFeedToken;

    @Override
    public void run(ApplicationArguments args) {
        if (!production) {
            return;
        }
        requireSecret("JWT_SECRET", jwtSecret, 32);
        requireSecret("NFC_AGENT_TOKEN", nfcAgentToken, 32);
        if (demoLoginEnabled) {
            throw new IllegalStateException("Demo login must be disabled in production");
        }
        if (initializeAdmin && isUnsafe(adminPassword)) {
            throw new IllegalStateException("Production admin bootstrap requires a strong APP_ADMIN_PASSWORD");
        }
        if (allowLocalhostLegacy) {
            throw new IllegalStateException("Legacy localhost NFC headers must be disabled in production");
        }
        if (publicIcsFeedWithoutToken) {
            throw new IllegalStateException("Public ICS feeds without tokens must be disabled in production");
        }
        requireSecret("REPORT_ICS_FEED_TOKEN", reportIcsFeedToken, 32);
    }

    private void requireSecret(String name, String value, int minLength) {
        if (value == null || value.isBlank() || value.length() < minLength || isUnsafe(value)) {
            throw new IllegalStateException(name + " must be set to a non-default secret of at least " + minLength + " characters");
        }
    }

    private boolean isUnsafe(String value) {
        return value == null || KNOWN_UNSAFE_VALUES.contains(value);
    }
}
