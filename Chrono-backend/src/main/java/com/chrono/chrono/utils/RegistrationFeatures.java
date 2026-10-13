package com.chrono.chrono.utils;

import com.chrono.chrono.entities.Company;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class RegistrationFeatures {

    private RegistrationFeatures() {
    }

    public static final Set<String> ALWAYS_AVAILABLE_FEATURES = Set.of("vacation", "nfc");

    public static final Set<String> OPTIONAL_FEATURES = Set.of(
            "payroll",
            "projects",
            "accounting",
            "crm",
            "supplyChain",
            "banking",
            "analytics",
            "signature",
            "chatbot",
            "premiumSupport",
            "roster",
            "pms"
    );

    public static final Set<String> ALL_FEATURES;

    static {
        ALL_FEATURES = new LinkedHashSet<>();
        ALL_FEATURES.addAll(ALWAYS_AVAILABLE_FEATURES);
        ALL_FEATURES.addAll(OPTIONAL_FEATURES);
    }

    public static LinkedHashSet<String> sanitizeOptionalFeatures(Collection<String> requestedKeys) {
        if (requestedKeys == null) {
            return new LinkedHashSet<>();
        }
        return requestedKeys.stream()
                .filter(OPTIONAL_FEATURES::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns the effective optional feature set for a company.
     *
     * <p>The historic {@code customerTrackingEnabled} column predates the
     * {@code company_enabled_features} table. Treat it as an alias for the
     * {@code projects} feature so that legacy tenants and newer tenants use the
     * same authorization semantics.</p>
     */
    public static LinkedHashSet<String> effectiveOptionalFeatures(Company company) {
        LinkedHashSet<String> features = company == null
                ? new LinkedHashSet<>()
                : sanitizeOptionalFeatures(company.getEnabledFeatures());
        if (company != null && Boolean.TRUE.equals(company.getCustomerTrackingEnabled())) {
            features.add("projects");
        }
        return features;
    }

    public static boolean isCompanyFeatureEnabled(Company company, String featureKey) {
        if (company == null || featureKey == null || featureKey.isBlank()) {
            return false;
        }
        if (ALWAYS_AVAILABLE_FEATURES.contains(featureKey)) {
            return true;
        }
        return effectiveOptionalFeatures(company).contains(featureKey);
    }

    public static boolean isProjectsEnabled(Company company) {
        return isCompanyFeatureEnabled(company, "projects");
    }
}
