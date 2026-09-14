package com.chrono.chrono.services.pms;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PmsSecretResolver {
    public Optional<String> resolve(String reference) {
        if (reference == null || !reference.startsWith("env:")) {
            return Optional.empty();
        }
        String name = reference.substring(4).trim();
        if (!name.matches("[A-Z][A-Z0-9_]{2,100}")) {
            return Optional.empty();
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
