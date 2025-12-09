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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
     * Logs in a demo user. If the demo account does not yet exist it will be
     * created with a default password and basic ROLE_USER permissions.
     *
     * @return AuthResponse containing a JWT for the demo user
     */
    public AuthResponse demoLogin() {
        final String username = "demo";
        final String rawPassword = "demo";

        // Find existing demo user or create a new one
        User user = userRepository.findByUsername(username).orElseGet(() -> {
            User demo = new User();
            demo.setUsername(username);
            demo.setPassword(passwordEncoder.encode(rawPassword));
            demo.setFirstName("Demo");
            demo.setLastName("User");
            // Required fields
            demo.setCountry("DE");
            demo.setPersonnelNumber("0");

            // Ensure the user has ROLE_USER and ROLE_ADMIN
            Role userRole = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
            Set<Role> roles = new HashSet<>();
            roles.add(userRole);
            roles.add(adminRole);

            demo.setRoles(roles);

            return demo;
        });

        // Ensure demo user always has required roles
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        Set<Role> roles = user.getRoles();
        if (!roles.contains(userRole)) {
            roles.add(userRole);
        }
        if (!roles.contains(adminRole)) {
            roles.add(adminRole);
        }

        user.setDemo(true);
        user = userRepository.save(user);

        demoDataService.resetDemoData(user);

        String token = jwtUtil.generateTokenWithUser(user);
        return new AuthResponse(token);
    }
}
