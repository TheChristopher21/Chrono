package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.ErrorResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.DemoLoginRateLimiter;
import com.chrono.chrono.services.LoginAttemptService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final DemoLoginRateLimiter demoLoginRateLimiter;

    @Value("${app.demo-login.enabled:false}")
    private boolean demoLoginEnabled;

    @Autowired
    public AuthController(AuthService authService,
                          JwtUtil jwtUtil,
                          UserService userService,
                          LoginAttemptService loginAttemptService,
                          DemoLoginRateLimiter demoLoginRateLimiter) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
        this.demoLoginRateLimiter = demoLoginRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        if (loginAttemptService.isRequestRateLimited(clientIp)) {
            return tooManyLoginAttempts();
        }
        if (request == null || !request.hasValidUsernameLength()) {
            throw new InvalidCredentialsException("Benutzername oder Passwort ist falsch.");
        }
        String attemptKey = request.getUsername().trim().toLowerCase(Locale.ROOT) + "@" + clientIp;
        if (loginAttemptService.isBlocked(attemptKey)) {
            return tooManyLoginAttempts();
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

        return ResponseEntity.ok(userService.getUserProfileByUsername(username));
    }

    private ResponseEntity<ErrorResponse> tooManyLoginAttempts() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("Zu viele fehlgeschlagene Anmeldeversuche. Bitte versuche es später erneut."));
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
        if (value.length() > 64) {
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
