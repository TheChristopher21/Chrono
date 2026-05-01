package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigAnnotationTest {

    @Test
    void securityConfig_enablesMethodSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class));
    }
}
