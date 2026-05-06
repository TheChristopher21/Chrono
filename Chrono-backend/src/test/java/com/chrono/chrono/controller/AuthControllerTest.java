package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.ErrorResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.DemoLoginRateLimiter;
import com.chrono.chrono.services.LoginAttemptService;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private AuthService authService;
    private LoginAttemptService loginAttemptService;
    private DemoLoginRateLimiter demoLoginRateLimiter;
    private AuthController controller;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        demoLoginRateLimiter = mock(DemoLoginRateLimiter.class);
        servletRequest = mock(HttpServletRequest.class);
        controller = new AuthController(
                authService,
                mock(JwtUtil.class),
                mock(UserDetailsService.class),
                null,
                mock(UserService.class),
                loginAttemptService,
                demoLoginRateLimiter,
                mock(UserPermissionService.class)
        );

        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(demoLoginRateLimiter.check("127.0.0.1")).thenReturn(new DemoLoginRateLimiter.RateLimitDecision(true, 0));
    }

    @Test
    void login_returnsTooManyRequestsWhenAttemptsAreBlocked() {
        AuthRequest request = new AuthRequest("john", "secret");
        when(loginAttemptService.isBlocked("john@127.0.0.1")).thenReturn(true);

        ResponseEntity<?> response = controller.login(request, servletRequest);

        assertEquals(429, response.getStatusCode().value());
        verify(authService, never()).login(any());
    }

    @Test
    void login_recordsFailureWhenCredentialsAreInvalid() {
        AuthRequest request = new AuthRequest("john", "wrong");
        when(loginAttemptService.isBlocked("john@127.0.0.1")).thenReturn(false);
        when(authService.login(request)).thenThrow(new InvalidCredentialsException("Benutzername oder Passwort ist falsch."));

        assertThrows(InvalidCredentialsException.class, () -> controller.login(request, servletRequest));

        verify(loginAttemptService).recordFailure("john@127.0.0.1");
        verify(loginAttemptService, never()).recordSuccess(anyString());
    }

    @Test
    void login_clearsFailuresAfterSuccess() {
        AuthRequest request = new AuthRequest("john", "secret");
        when(loginAttemptService.isBlocked("john@127.0.0.1")).thenReturn(false);
        when(authService.login(request)).thenReturn(new AuthResponse("jwt-token"));

        ResponseEntity<?> response = controller.login(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(loginAttemptService).recordSuccess("john@127.0.0.1");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void login_usesLastForwardedAddressFromTrustedProxy() {
        AuthRequest request = new AuthRequest("john", "secret");
        when(servletRequest.getRemoteAddr()).thenReturn("172.20.0.10");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.44, 203.0.113.7");
        when(loginAttemptService.isBlocked("john@203.0.113.7")).thenReturn(false);
        when(authService.login(request)).thenReturn(new AuthResponse("jwt-token"));

        ResponseEntity<?> response = controller.login(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(loginAttemptService).recordSuccess("john@203.0.113.7");
    }

    @Test
    void login_ignoresForwardedAddressFromUntrustedRemote() {
        AuthRequest request = new AuthRequest("john", "secret");
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.99");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.44");
        when(loginAttemptService.isBlocked("john@203.0.113.99")).thenReturn(false);
        when(authService.login(request)).thenReturn(new AuthResponse("jwt-token"));

        ResponseEntity<?> response = controller.login(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(loginAttemptService).recordSuccess("john@203.0.113.99");
    }

    @Test
    void demoLogin_isDeniedWhenFeatureIsDisabled() {
        ReflectionTestUtils.setField(controller, "demoLoginEnabled", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.demoLogin(servletRequest));

        assertEquals(404, ex.getStatusCode().value());
        verify(authService, never()).demoLogin();
    }

    @Test
    void demoLogin_returnsTooManyRequestsWhenRateLimited() {
        ReflectionTestUtils.setField(controller, "demoLoginEnabled", true);
        when(demoLoginRateLimiter.check("127.0.0.1")).thenReturn(new DemoLoginRateLimiter.RateLimitDecision(false, 120));

        ResponseEntity<?> response = controller.demoLogin(servletRequest);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
        assertEquals("Zu viele Demo-Starts. Bitte versuche es spaeter erneut.", ((ErrorResponse) response.getBody()).getMessage());
        verify(authService, never()).demoLogin();
    }
}
