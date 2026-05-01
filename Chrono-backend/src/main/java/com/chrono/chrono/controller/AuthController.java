package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.dto.ErrorResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.LoginAttemptService;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final LoginAttemptService loginAttemptService;
    private final UserPermissionService userPermissionService;

    @Value("${app.demo-login.enabled:false}")
    private boolean demoLoginEnabled;

    @Autowired
    public AuthController(AuthService authService,
                          JwtUtil jwtUtil,
                          UserDetailsService userDetailsService,
                          UserRepository userRepository,
                          UserService userService,
                          LoginAttemptService loginAttemptService,
                          UserPermissionService userPermissionService) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
        this.userPermissionService = userPermissionService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String attemptKey = buildAttemptKey(request, httpRequest);
        if (loginAttemptService.isBlocked(attemptKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("Zu viele fehlgeschlagene Anmeldeversuche. Bitte versuche es später erneut."));
        }

        try {
            AuthResponse response = authService.login(request);
            loginAttemptService.recordSuccess(attemptKey);
            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException ex) {
            loginAttemptService.recordFailure(attemptKey);
            throw ex;
        }
    }

    @PostMapping("/demo")
    public ResponseEntity<?> demoLogin() {
        if (!demoLoginEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo login is disabled");
        }
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
        profile.setPagePermissions(userPermissionService.resolvePagePermissions(user));
        return ResponseEntity.ok(profile);
    }

    private String buildAttemptKey(AuthRequest request, HttpServletRequest httpRequest) {
        String username = request != null && request.getUsername() != null
                ? request.getUsername().trim().toLowerCase()
                : "anonymous";
        return username + "@" + resolveClientIp(httpRequest);
    }

    private String resolveClientIp(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return "unknown";
        }

        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = httpRequest.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return httpRequest.getRemoteAddr();
    }
}
