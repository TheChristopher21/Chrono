package com.chrono.chrono.utils;

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
            "roster"
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
}
