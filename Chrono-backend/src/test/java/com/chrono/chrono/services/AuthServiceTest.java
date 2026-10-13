package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.exceptions.InvalidCredentialsException;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private DemoDataService demoDataService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_returnsToken_whenCredentialsValid() {
        AuthRequest request = new AuthRequest("john", "pass");
        User user = new User();
        user.setUsername("john");
        user.setPassword("encoded");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(jwtUtil.generateTokenWithUser(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        verify(userRepository).findByUsername("john");
        verify(passwordEncoder).matches("pass", "encoded");
        verify(jwtUtil).generateTokenWithUser(user);
    }

    @Test
    void login_throwsException_whenPasswordInvalid() {
        AuthRequest request = new AuthRequest("john", "wrong");
        User user = new User();
        user.setUsername("john");
        user.setPassword("encoded");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Benutzername oder Passwort ist falsch.", ex.getMessage());
        verify(passwordEncoder).matches("wrong", "encoded");
    }

    @Test
    void login_throwsException_whenUserNotFound() {
        AuthRequest request = new AuthRequest("unknown", "pass");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Benutzername oder Passwort ist falsch.", ex.getMessage());
        verify(userRepository).findByUsername("unknown");
    }

    @Test
    void login_throwsException_whenUserIsDeleted() {
        AuthRequest request = new AuthRequest("john", "pass");
        User user = new User();
        user.setUsername("john");
        user.setPassword("encoded");
        user.setDeleted(true);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Benutzername oder Passwort ist falsch.", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateTokenWithUser(any(User.class));
    }

    @Test
    void login_throwsException_whenDemoUserIsExpired() {
        AuthRequest request = new AuthRequest("demo_old", "pass");
        User user = new User();
        user.setUsername("demo_old");
        user.setPassword("encoded");
        user.setDemo(true);
        user.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByUsername("demo_old")).thenReturn(Optional.of(user));

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Benutzername oder Passwort ist falsch.", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateTokenWithUser(any(User.class));
    }

    @Test
    void demoLogin_createsIsolatedExpiringDemoUser() {
        Role roleUser = new Role("ROLE_USER");
        Role roleAdmin = new Role("ROLE_ADMIN");
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(roleUser));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateTokenWithUser(any(User.class))).thenReturn("demo-token");

        AuthResponse res = authService.demoLogin();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertTrue(saved.getUsername().startsWith("demo_"));
        assertTrue(saved.isDemo());
        assertNotNull(saved.getDemoSessionId());
        assertNotNull(saved.getDemoExpiresAt());
        assertTrue(saved.getRoles().contains(roleUser));
        assertTrue(saved.getRoles().contains(roleAdmin));
        verify(demoDataService).resetDemoData(saved);
        assertEquals("demo-token", res.getToken());
    }

    @Test
    void demoLogin_createsFreshUserOnEveryCall() {
        Role roleUser = new Role("ROLE_USER");
        Role roleAdmin = new Role("ROLE_ADMIN");
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(roleUser));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateTokenWithUser(any(User.class))).thenReturn("token-1", "token-2");

        AuthResponse first = authService.demoLogin();
        AuthResponse second = authService.demoLogin();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        assertNotEquals(userCaptor.getAllValues().get(0).getUsername(), userCaptor.getAllValues().get(1).getUsername());
        verify(demoDataService, times(2)).resetDemoData(any(User.class));
        assertEquals("token-1", first.getToken());
        assertEquals("token-2", second.getToken());
    }
}

