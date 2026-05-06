package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.dto.ErrorResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.DemoLoginRateLimiter;
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

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final DemoLoginRateLimiter demoLoginRateLimiter;
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
                          DemoLoginRateLimiter demoLoginRateLimiter,
                          UserPermissionService userPermissionService) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
        this.demoLoginRateLimiter = demoLoginRateLimiter;
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
    public ResponseEntity<?> demoLogin(HttpServletRequest httpRequest) {
        if (!demoLoginEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo login is disabled");
        }
        DemoLoginRateLimiter.RateLimitDecision limit = demoLoginRateLimiter.check(resolveClientIp(httpRequest));
        if (!limit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limit.retryAfterSeconds()))
                    .body(new ErrorResponse("Zu viele Demo-Starts. Bitte versuche es spaeter erneut."));
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

        String remoteAddr = cleanIpCandidate(httpRequest.getRemoteAddr());
        if (isTrustedProxyRemote(remoteAddr)) {
            String forwardedFor = lastForwardedForAddress(httpRequest.getHeader("X-Forwarded-For"));
            if (forwardedFor != null) {
                return forwardedFor;
            }

            String realIp = cleanIpCandidate(httpRequest.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }

        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String lastForwardedForAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }

        String[] parts = forwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = cleanIpCandidate(parts[i]);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String cleanIpCandidate(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || candidate.contains("\r") || candidate.contains("\n")) {
            return null;
        }
        return candidate;
    }

    private boolean isTrustedProxyRemote(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
