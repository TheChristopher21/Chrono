package com.chrono.chrono.services;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.User;
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

        assertThrows(RuntimeException.class, () -> authService.login(request));
        verify(passwordEncoder).matches("wrong", "encoded");
    }

    @Test
    void login_throwsException_whenUserNotFound() {
        AuthRequest request = new AuthRequest("unknown", "pass");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(request));
        verify(userRepository).findByUsername("unknown");
    }
}

