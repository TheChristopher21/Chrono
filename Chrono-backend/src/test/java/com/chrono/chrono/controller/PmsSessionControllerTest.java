package com.chrono.chrono.controller;

import com.chrono.chrono.controller.pms.PmsSessionController;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsSessionControllerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserPermissionService permissions = mock(UserPermissionService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final PmsSessionController controller = new PmsSessionController(users, permissions, jwt);
    private final Principal principal = () -> "reception";
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("reception");
        user.setCompany(new Company());
        when(users.findByUsernameWithPermissionContext("reception")).thenReturn(Optional.of(user));
        when(jwt.generateTokenWithUser(user)).thenReturn("renewed-finite-jwt");
    }

    @Test
    void renewsNormalTokenOnlyAfterCheckingPmsAccess() {
        var response = controller.refresh(principal);
        assertEquals("renewed-finite-jwt", response.getBody().getToken());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var order = inOrder(permissions, jwt);
        order.verify(permissions).assertPageAccess(eq(user), eq("pms"), eq("VIEW"), anyString());
        order.verify(jwt).generateTokenWithUser(user);
    }

    @Test
    void requiresAuthenticatedPrincipal() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.refresh(null)).getStatusCode());
        verifyNoInteractions(jwt, permissions);
    }

    @Test
    void refusesRemovedPmsPermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(permissions).assertPageAccess(eq(user), eq("pms"), eq("VIEW"), anyString());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.refresh(principal)).getStatusCode());
        verifyNoInteractions(jwt);
    }

    @Test
    void refusesDeletedUserDespiteCachedAuthentication() {
        user.setDeleted(true);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.refresh(principal)).getStatusCode());
        verifyNoInteractions(jwt);
    }

    @Test
    void doesNotExtendExpiredDemoTenants() {
        user.setDemo(true);
        user.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.refresh(principal)).getStatusCode());
        verifyNoInteractions(jwt);
    }

    @Test
    void refusesAccountsWithoutCompany() {
        user.setCompany(null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.refresh(principal)).getStatusCode());
        verifyNoInteractions(jwt);
    }
}
