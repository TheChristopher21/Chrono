package com.chrono.chrono.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFeaturesTest {

    @Test
    void keepsPmsAsAnOptionalCompanyFeature() {
        assertThat(RegistrationFeatures.OPTIONAL_FEATURES).contains("pms");
        assertThat(RegistrationFeatures.sanitizeOptionalFeatures(List.of("pms", "unknown")))
                .containsExactly("pms");
    }
}
