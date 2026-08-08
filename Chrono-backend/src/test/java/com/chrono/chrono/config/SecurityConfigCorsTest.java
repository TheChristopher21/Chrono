package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsTest {

    @Test
    void parsesExplicitOriginsAndRemovesDuplicates() {
        assertThat(SecurityConfig.parseOrigins(
                "https://chrono-logisch.ch, https://www.chrono-logisch.ch,https://chrono-logisch.ch"))
                .containsExactly("https://chrono-logisch.ch", "https://www.chrono-logisch.ch");
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThatThrownBy(() -> SecurityConfig.parseOrigins("https://*.chrono-logisch.ch"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CORS origins must be an explicit non-empty list");
    }
}
