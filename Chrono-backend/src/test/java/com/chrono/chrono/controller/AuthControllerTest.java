package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.services.AuthService;
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
    private AuthController controller;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        servletRequest = mock(HttpServletRequest.class);
        controller = new AuthController(
                authService,
                mock(JwtUtil.class),
                mock(UserDetailsService.class),
                null,
                mock(UserService.class),
                loginAttemptService,
                mock(UserPermissionService.class)
        );

        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
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
    void demoLogin_isDeniedWhenFeatureIsDisabled() {
        ReflectionTestUtils.setField(controller, "demoLoginEnabled", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.demoLogin());

        assertEquals(404, ex.getStatusCode().value());
        verify(authService, never()).demoLogin();
    }
}
