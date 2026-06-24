package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private DemoDataService demoDataService;

    @Value("${app.demo-login.session-ttl:PT24H}")
    private Duration demoSessionTtl;

    private static final String INVALID_CREDENTIALS_MESSAGE = "Benutzername oder Passwort ist falsch.";

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (user.getPassword() == null) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String storedPassword = user.getPassword();
        String normalizedPassword = normalizeLegacyBcryptHash(storedPassword);

        if (!storedPassword.equals(normalizedPassword)) {
            user.setPassword(normalizedPassword);
            userRepository.save(user);
        }

        if (!passwordEncoder.matches(request.getPassword(), normalizedPassword)) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        // Token (mit user-Daten gehasht) generieren
        String token = jwtUtil.generateTokenWithUser(user);
        return new AuthResponse(token);
    }

    private String normalizeLegacyBcryptHash(String password) {
        if (password == null) {
            return null;
        }

        // Some legacy hashes were stored without the BCrypt version prefix (e.g. "$10$...").
        // Spring's BCryptPasswordEncoder expects the full prefix ("$2a$10$...") and
        // rejects the shortened variant. To keep affected users able to log in, pad the
        // missing "$2a" prefix once and persist the corrected hash.
        if (password.startsWith("$") && !password.startsWith("$2")) {
            return "$2a" + password;
        }

        return password;
    }

    /**
     * Creates an isolated, expiring demo tenant and logs in its demo admin.
     *
     * @return AuthResponse containing a JWT for the demo user
     */
    public AuthResponse demoLogin() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String username = "demo_" + sessionId.substring(0, 12);
        String rawPassword = UUID.randomUUID().toString();
        Duration sessionTtl = demoSessionTtl != null ? demoSessionTtl : Duration.ofHours(24);
        LocalDateTime expiresAt = LocalDateTime.now().plus(sessionTtl);

        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        roles.add(adminRole);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFirstName("Demo");
        user.setLastName("Manager");
        user.setEmail("demo+" + sessionId.substring(0, 12) + "@chrono-demo.ch");
        user.setCountry("DE");
        user.setPersonnelNumber("D-1000");
        user.setRoles(roles);

        user.setDemo(true);
        user.setDemoSessionId(sessionId);
        user.setDemoExpiresAt(expiresAt);
        user = userRepository.save(user);

        demoDataService.resetDemoData(user);

        String token = jwtUtil.generateTokenWithUser(user);
        return new AuthResponse(token);
    }
}
