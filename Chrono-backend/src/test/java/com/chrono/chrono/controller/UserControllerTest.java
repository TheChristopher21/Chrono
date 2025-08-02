package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChangePasswordRequest;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserControllerTest {

    @Test
    void changePassword_success() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserController controller = new UserController(userService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);

        ChangePasswordRequest request = new ChangePasswordRequest("john", "old", "new");
        User user = new User();
        user.setUsername("john");
        user.setPassword("encodedOld");
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        ResponseEntity<String> response = controller.changePassword(request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Password updated successfully", response.getBody());
        verify(userRepository).save(user);
        assertEquals("encodedNew", user.getPassword());
    }

    @Test
    void changePassword_userNotFound() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserController controller = new UserController(userService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);

        ChangePasswordRequest request = new ChangePasswordRequest("missing", "old", "new");
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> controller.changePassword(request));
    }
}
