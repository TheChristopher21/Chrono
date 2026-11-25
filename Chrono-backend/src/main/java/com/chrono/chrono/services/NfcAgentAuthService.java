package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class NfcAgentAuthService {
    public record AgentIdentity(Long companyId, String deviceId) {}
    private record Registration(AgentIdentity identity, String token, boolean defaultForCompany, boolean localHardware) {}

    private final ObjectMapper mapper;
    private final Environment environment;
    private final CompanyRepository companies;

    public NfcAgentAuthService(ObjectMapper mapper, Environment environment, CompanyRepository companies) {
        this.mapper = mapper;
        this.environment = environment;
        this.companies = companies;
    }

    @Value("${nfc.agent.token:}")
    private String agentToken;

    @Value("${nfc.agent.company-id:}")
    private String legacyCompanyId;

    @Value("${nfc.agent.device-id:}")
    private String legacyDeviceId;

    @Value("${nfc.agent.registry-json:[]}")
    private String registryJson;

    @Value("${nfc.agent.allow-localhost-legacy:false}")
    private boolean allowLocalhostLegacy;

    public AgentIdentity requireAgent(String token, String legacyHeader, HttpServletRequest request) {
        List<Registration> registrations = registrations();
        for (Registration registration : registrations) {
            if (matches(registration.token(), token)) return requireExistingCompany(registration.identity());
        }
        if (allowLocalhostLegacy && "true".equalsIgnoreCase(legacyHeader) && isLoopback(request)) {
            AgentIdentity legacy = legacyIdentity();
            if (legacy != null) return requireExistingCompany(legacy);
        }
        throw new AccessDeniedException("NFC agent authentication with configured company/device ownership required");
    }

    public boolean isAgentRequest(String token, String legacyHeader, HttpServletRequest request) {
        try {
            requireAgent(token, legacyHeader, request);
            return true;
        } catch (AccessDeniedException | ResponseStatusException ex) {
            return false;
        }
    }

    public boolean matchesConfiguredToken(String token) {
        return legacyIdentity() != null && matches(agentToken, token);
    }

    public AgentIdentity routeForCompany(Long companyId) {
        List<Registration> routes = registrations().stream()
                .filter(item -> Objects.equals(companyId, item.identity().companyId())).toList();
        List<Registration> defaults = routes.stream().filter(Registration::defaultForCompany).toList();
        if (defaults.size() == 1) return requireExistingCompany(defaults.get(0).identity());
        if (defaults.isEmpty() && routes.size() == 1) return requireExistingCompany(routes.get(0).identity());
        throw unavailable("Configure one default NFC device for this company in NFC_AGENT_REGISTRY_JSON (or explicit legacy ownership)");
    }

    /** Direct hardware endpoints operate the reader attached to this backend only. */
    public void requireLocalHardware(AgentIdentity identity) {
        boolean local = registrations().stream().anyMatch(item -> item.identity().equals(identity) && item.localHardware());
        if (!local) throw new AccessDeniedException("This NFC agent does not own the backend's local reader");
    }

    public void requireUserInCompany(AgentIdentity identity, User target) {
        if (target == null || target.getCompany() == null
                || !Objects.equals(identity.companyId(), target.getCompany().getId())) {
            throw new AccessDeniedException("NFC agent cannot access this company user");
        }
    }

    private AgentIdentity requireExistingCompany(AgentIdentity identity) {
        if (!companies.existsById(identity.companyId())) throw unavailable("Configured NFC company does not exist; review NFC ownership configuration");
        return identity;
    }

    // Parse lazily so absent/malformed NFC configuration cannot prevent unrelated application startup.
    private List<Registration> registrations() {
        List<Registration> registrations = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(isBlank(registryJson) ? "[]" : registryJson);
            if (root == null || !root.isArray()) throw new IllegalArgumentException();
            for (JsonNode node : root) {
                if (!node.path("companyId").isIntegralNumber() || !node.path("companyId").canConvertToLong()
                        || node.path("companyId").asLong() <= 0) throw new IllegalArgumentException();
                String device = node.path("deviceId").asText("");
                String tokenEnv = node.path("tokenEnv").asText("");
                if (!validDevice(device) || !tokenEnv.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException();
                String secret = environment.getProperty(tokenEnv);
                if (isBlank(secret)) throw new IllegalArgumentException();
                registrations.add(new Registration(new AgentIdentity(node.path("companyId").asLong(), device),
                        secret, node.path("defaultForCompany").asBoolean(false), node.path("localHardware").asBoolean(false)));
            }
            AgentIdentity legacy = legacyIdentity();
            if (!isBlank(agentToken) && legacy != null) registrations.add(new Registration(legacy, agentToken, false, true));
            Set<AgentIdentity> identities = new HashSet<>();
            Set<String> secrets = new HashSet<>();
            int localReaders = 0;
            for (Registration registration : registrations) {
                if (!identities.add(registration.identity()) || !secrets.add(registration.token().trim())) throw new IllegalArgumentException();
                if (registration.localHardware()) localReaders++;
            }
            if (localReaders > 1) throw new IllegalArgumentException();
            return registrations;
        } catch (Exception ex) {
            throw unavailable("Invalid NFC agent registry: configure unique company/device identities, tokenEnv secrets and at most one localHardware reader");
        }
    }

    private AgentIdentity legacyIdentity() {
        if (isBlank(legacyCompanyId) || !validDevice(legacyDeviceId)) return null;
        try {
            long id = Long.parseLong(legacyCompanyId);
            return id > 0 ? new AgentIdentity(id, legacyDeviceId) : null;
        } catch (NumberFormatException ex) { return null; }
    }

    private boolean validDevice(String device) {
        return device != null && device.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private boolean matches(String expectedToken, String token) {
        if (isBlank(expectedToken) || isBlank(token)) return false;
        byte[] expected = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
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
