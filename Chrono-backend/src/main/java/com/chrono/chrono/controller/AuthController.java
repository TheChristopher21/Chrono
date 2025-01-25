package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest) {
        try {
            AuthResponse response = authService.authenticate(authRequest);
            System.out.println("Login successful for user: " + authRequest.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Login Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(
                    new AuthResponse(authRequest.getUsername(), null, null, "Login failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest authRequest) {
        try {
            authService.register(authRequest);
            System.out.println("User registered successfully: " + authRequest.getUsername());
            return ResponseEntity.ok("User registered successfully");
        } catch (Exception e) {
            System.err.println("Registration Error: " + e.getMessage());
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }
}
