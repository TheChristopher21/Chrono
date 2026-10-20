package com.chrono.chrono.utils;

import com.chrono.chrono.entities.Company;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFeaturesTest {

    @Test
    void keepsPmsAsAnOptionalCompanyFeature() {
        assertThat(RegistrationFeatures.OPTIONAL_FEATURES).contains("pms");
        assertThat(RegistrationFeatures.sanitizeOptionalFeatures(List.of("pms", "unknown")))
                .containsExactly("pms");
    }

    @Test
    void enablesProjectsFromModernFeatureWhenLegacyFlagIsFalse() {
        Company company = new Company("Modern AG");
        company.setCustomerTrackingEnabled(false);
        company.setEnabledFeatures(Set.of("projects"));

        assertThat(RegistrationFeatures.isProjectsEnabled(company)).isTrue();
        assertThat(RegistrationFeatures.effectiveOptionalFeatures(company))
                .containsExactly("projects");
    }

    @Test
    void enablesProjectsFromLegacyFlagAndPublishesModernAlias() {
        Company company = new Company("Legacy AG");
        company.setCustomerTrackingEnabled(true);
        company.setEnabledFeatures(Set.of());

        assertThat(RegistrationFeatures.isProjectsEnabled(company)).isTrue();
        assertThat(RegistrationFeatures.effectiveOptionalFeatures(company))
                .containsExactly("projects");
    }

    @Test
    void rejectsCompanyScopedFeaturesWithoutACompany() {
        assertThat(RegistrationFeatures.isProjectsEnabled(null)).isFalse();
        assertThat(RegistrationFeatures.isCompanyFeatureEnabled(null, "projects")).isFalse();
    }
}
