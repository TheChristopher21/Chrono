package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.LoginRequest;
import com.chrono.chrono.dto.RegisterRequest;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;

    }

    public AuthResponse register(RegisterRequest registerRequest) {
        System.out.println("Register request received for username: " + registerRequest.getUsername());

        // Überprüfe, ob der Benutzername bereits existiert
        Optional<User> existingUser = userRepository.findByUsername(registerRequest.getUsername());
        if (existingUser.isPresent()) {
            System.out.println("Username already exists: " + registerRequest.getUsername());
            throw new RuntimeException("Username already exists!");
        }

        System.out.println("Username is available: " + registerRequest.getUsername());

        // Benutzer erstellen
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        System.out.println("Encoded password: " + user.getPassword());

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default role not found!"));
        user.getRoles().add(userRole);

        System.out.println("User role assigned: " + userRole.getName());

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        System.out.println("Generated token for registration: " + token);

        return new AuthResponse(token);
    }



    public AuthResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password!"));

        System.out.println("Eingegebenes Passwort: " + loginRequest.getPassword());
        System.out.println("Gehashtes Passwort in DB: " + user.getPassword());

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password!");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token);
    }



}
