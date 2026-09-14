package com.chrono.chrono.services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class NfcAgentAuthService {

    @Value("${nfc.agent.token:}")
    private String agentToken;

    @Value("${nfc.agent.allow-localhost-legacy:false}")
    private boolean allowLocalhostLegacy;

    public void requireAgent(String token, String legacyHeader, HttpServletRequest request) {
        if (!isAgentRequest(token, legacyHeader, request)) {
            throw new AccessDeniedException("NFC agent authentication required");
        }
    }

    public boolean isAgentRequest(String token, String legacyHeader, HttpServletRequest request) {
        if (matchesConfiguredToken(token)) {
            return true;
        }
        return allowLocalhostLegacy && "true".equalsIgnoreCase(legacyHeader) && isLoopback(request);
    }

    public boolean matchesConfiguredToken(String token) {
        if (isBlank(agentToken) || isBlank(token)) {
            return false;
        }
        byte[] expected = agentToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.trim().getBytes(StandardCharsets.UTF_8);
        return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
    }

    private boolean isLoopback(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(remoteAddr);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
