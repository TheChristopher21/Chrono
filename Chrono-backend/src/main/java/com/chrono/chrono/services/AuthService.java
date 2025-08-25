package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
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

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Token (mit user-Daten gehasht) generieren
        String token = jwtUtil.generateTokenWithUser(user);
        return new AuthResponse(token);
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
