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

    @Value("${app.demo-login.allow-production:false}")
    private boolean demoLoginAllowedInProduction;

    @Value("${app.pms.test-account.enabled:false}")
    private boolean pmsTestAccountEnabled;

    @Value("${app.pms.demo-data.enabled:false}")
    private boolean pmsDemoDataEnabled;

    @Value("${app.security.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.pms.provider-gateway.enabled:false}")
    private boolean pmsProviderGatewayEnabled;

    @Value("${app.pms.provider-gateway.endpoint:}")
    private String pmsProviderGatewayEndpoint;

    @Value("${app.pms.provider-gateway.secret:}")
    private String pmsProviderGatewaySecret;

    @Value("${app.pms.payments.simulated.enabled:false}")
    private boolean simulatedPmsPaymentsEnabled;

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
        if (demoLoginEnabled || demoLoginAllowedInProduction) {
            throw new IllegalStateException("Demo login must be disabled in production");
        }
        if (pmsTestAccountEnabled || pmsDemoDataEnabled) {
            throw new IllegalStateException("PMS test accounts and demo data must be disabled in production");
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
        validateProductionOrigins(allowedOrigins);
        if (pmsProviderGatewayEnabled) {
            if (pmsProviderGatewayEndpoint == null
                    || !pmsProviderGatewayEndpoint.toLowerCase().startsWith("https://")) {
                throw new IllegalStateException("Production PMS provider gateway must use HTTPS");
            }
            requireSecret("APP_PMS_PROVIDER_GATEWAY_SECRET", pmsProviderGatewaySecret, 32);
        }
        if (simulatedPmsPaymentsEnabled) {
            throw new IllegalStateException("Simulated PMS payments must be disabled in production");
        }
    }

    private void requireSecret(String name, String value, int minLength) {
        if (value == null || value.isBlank() || value.length() < minLength || isUnsafe(value)) {
            throw new IllegalStateException(name + " must be set to a non-default secret of at least " + minLength + " characters");
        }
    }

    private boolean isUnsafe(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase();
        return KNOWN_UNSAFE_VALUES.stream().anyMatch(unsafe -> unsafe.equalsIgnoreCase(value.trim()))
                || normalized.contains("replace-with")
                || normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.endsWith(".invalid");
    }

    private void validateProductionOrigins(String origins) {
        for (String origin : SecurityConfig.parseOrigins(origins)) {
            String lower = origin.toLowerCase();
            if (!lower.startsWith("https://")
                    || lower.contains("localhost")
                    || lower.contains("127.0.0.1")
                    || lower.startsWith("https://10.")
                    || lower.startsWith("https://192.168.")) {
                throw new IllegalStateException("Production CORS origins must use explicit public HTTPS URLs");
            }
        }
    }
}
