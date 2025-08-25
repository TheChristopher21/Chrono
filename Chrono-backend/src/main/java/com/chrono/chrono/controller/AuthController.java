package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final UserService userService;

    @Autowired
    public AuthController(AuthService authService,
                          JwtUtil jwtUtil,
                          UserDetailsService userDetailsService,
                          UserRepository userRepository,
                          UserService userService) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/demo")
    public ResponseEntity<?> demoLogin() {
        AuthResponse response = authService.demoLogin();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültiges Token!");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        // 🧠 Nutzt jetzt userService, der `trackingBalanceInMinutes` absichert
        User user = userService.getUserByUsername(username);

        UserDTO profile = new UserDTO(user);
        return ResponseEntity.ok(profile);
    }
}
