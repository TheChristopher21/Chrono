package com.chrono.chrono.services.pms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PmsSecretResolverTest {
    private final PmsSecretResolver resolver = new PmsSecretResolver();

    @Test
    void rejectsPlaintextAndUnsafeSecretReferences() {
        assertThat(resolver.resolve("mein-klartext-secret")).isEmpty();
        assertThat(resolver.resolve("env:path-traversal")).isEmpty();
        assertThat(resolver.resolve("env:ab")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }
}
