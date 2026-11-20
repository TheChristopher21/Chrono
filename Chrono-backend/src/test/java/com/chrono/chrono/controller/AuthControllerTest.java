package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.ErrorResponse;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.services.AuthService;
import com.chrono.chrono.services.DemoLoginRateLimiter;
import com.chrono.chrono.services.LoginAttemptService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private AuthService authService;
    private LoginAttemptService loginAttemptService;
    private DemoLoginRateLimiter demoLoginRateLimiter;
    private JwtUtil jwtUtil;
    private UserService userService;
    private AuthController controller;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        demoLoginRateLimiter = mock(DemoLoginRateLimiter.class);
        jwtUtil = mock(JwtUtil.class);
        userService = mock(UserService.class);
        servletRequest = mock(HttpServletRequest.class);
        controller = new AuthController(
                authService,
                jwtUtil,
                userService,
                loginAttemptService,
                demoLoginRateLimiter
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
    void login_rejectsLongAndMissingUsernamesBeforeAccountStateOrDatabaseAccess() {
        for (String username : new String[]{null, "", "x".repeat(256), "😀".repeat(256)}) {
            assertThrows(InvalidCredentialsException.class,
                    () -> controller.login(new AuthRequest(username, "wrong"), servletRequest));
        }

        verifyNoInteractions(authService);
        verify(loginAttemptService, never()).isBlocked(anyString());
        verify(loginAttemptService, never()).recordFailure(anyString());
        verify(loginAttemptService, times(4)).isRequestRateLimited("127.0.0.1");
    }

    @Test
    void login_preservesExistingUsernamesAtTheStorageLimitIncludingUnicode() {
        for (String username : new String[]{"X".repeat(255), "😀".repeat(255), " Exact Name "}) {
            AuthRequest request = new AuthRequest(username, "secret");
            when(authService.login(request)).thenReturn(new AuthResponse("jwt-token"));

            assertEquals(200, controller.login(request, servletRequest).getStatusCode().value());

            verify(authService).login(request);
            assertEquals(username, request.getUsername());
        }
    }

    @Test
    void login_requestLimiterRunsBeforeUsernameStateAndAuthentication() {
        when(loginAttemptService.isRequestRateLimited("127.0.0.1")).thenReturn(true);

        ResponseEntity<?> response = controller.login(new AuthRequest("rotating", "wrong"), servletRequest);

        assertEquals(429, response.getStatusCode().value());
        verify(loginAttemptService, never()).isBlocked(anyString());
        verify(loginAttemptService, never()).recordFailure(anyString());
        verifyNoInteractions(authService);
    }

    @Test
    void login_rotatingUsernamesReachTheIpLimitBeforeMoreDatabaseAttempts() {
        LoginAttemptService actualAttempts = new LoginAttemptService();
        AuthController actualController = new AuthController(authService, jwtUtil, userService, actualAttempts, demoLoginRateLimiter);
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Benutzername oder Passwort ist falsch."));
        for (int i = 0; i < 120; i++) {
            AuthRequest request = new AuthRequest("random-" + i, "wrong");
            assertThrows(InvalidCredentialsException.class, () -> actualController.login(request, servletRequest));
        }

        assertEquals(429, actualController.login(new AuthRequest("another-user", "wrong"), servletRequest).getStatusCode().value());
        verify(authService, times(120)).login(any());
        assertFalse(((java.util.Map<?, ?>) ReflectionTestUtils.getField(actualAttempts, "attempts")).containsKey("another-user@127.0.0.1"));
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

    @Test
    void getCurrentUser_returnsTheTransactionallyBuiltProfile() {
        UserDTO profile = new UserDTO();
        profile.setUsername("john");
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.extractUsername("valid-token")).thenReturn("john");
        when(userService.getUserProfileByUsername("john")).thenReturn(profile);

        ResponseEntity<?> response = controller.getCurrentUser(servletRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(profile, response.getBody());
        verify(userService).getUserProfileByUsername("john");
    }

    @Test
    void getCurrentUser_rejectsRequestsWithoutBearerToken() {
        when(servletRequest.getHeader("Authorization")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCurrentUser(servletRequest)
        );

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(jwtUtil, userService);
    }
}
