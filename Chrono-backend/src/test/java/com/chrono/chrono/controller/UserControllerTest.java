package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChangePasswordRequest;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserControllerTest {

    private UserService userService;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserPermissionService userPermissionService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userPermissionService = mock(UserPermissionService.class);
        controller = new UserController(userService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(controller, "userPermissionService", userPermissionService);
    }

    @Test
    void changePassword_successForAuthenticatedUser() {
        ChangePasswordRequest request = new ChangePasswordRequest("john", "old", "new");
        User user = user("john", null, "ROLE_USER");
        user.setPassword("encodedOld");
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        ResponseEntity<String> response = controller.changePassword(request, principal("john"));

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Password updated successfully", response.getBody());
        verify(userRepository).save(user);
        assertEquals("encodedNew", user.getPassword());
    }

    @Test
    void changePassword_userNotFound() {
        ChangePasswordRequest request = new ChangePasswordRequest("missing", "old", "new");
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> controller.changePassword(request, principal("missing")));
    }

    @Test
    void changePassword_invalidStoredPassword_returnsBadRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest("john", "old", "new");
        User user = user("john", null, "ROLE_USER");
        user.setPassword("plain");
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "plain")).thenThrow(new IllegalArgumentException("not bcrypt"));

        ResponseEntity<String> response = controller.changePassword(request, principal("john"));

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("Current password is incorrect", response.getBody());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_updatesAdminPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("admin", "old", "new");
        User user = user("admin", null, "ROLE_ADMIN");
        user.setPassword("encodedOld");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        ResponseEntity<String> response = controller.changePassword(request, principal("admin"));

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Password updated successfully", response.getBody());
        assertEquals("encodedNew", user.getPassword());
        assertEquals("encodedNew", user.getAdminPassword());
    }

    @Test
    void changePassword_forbiddenForDifferentUser() {
        ChangePasswordRequest request = new ChangePasswordRequest("john", "old", "new");
        User actor = user("alice", company(1L), "ROLE_USER");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(actor));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.changePassword(request, principal("alice"))
        );

        assertEquals(403, ex.getStatusCode().value());
        verify(userRepository).findByUsername("alice");
        verify(userRepository, never()).findByUsername("john");
    }

    @Test
    void getProfile_allowsAdminFromSameCompany() {
        Company company = company(1L);
        User admin = user("admin", company, "ROLE_ADMIN");
        User target = user("john", company, "ROLE_USER");
        target.setFirstName("John");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userService.getUserByUsername("john")).thenReturn(target);

        UserDTO dto = controller.getProfile("john", principal("admin"));

        assertEquals("john", dto.getUsername());
        assertEquals("John", dto.getFirstName());
    }

    @Test
    void getProfile_forbidsAdminFromDifferentCompany() {
        User admin = user("admin", company(1L), "ROLE_ADMIN");
        User target = user("john", company(2L), "ROLE_USER");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userService.getUserByUsername("john")).thenReturn(target);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.getProfile("john", principal("admin"))
        );

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void update_forbidsEditingAnotherUsersProfile() {
        User actor = user("alice", company(1L), "ROLE_USER");
        User target = user("john", company(1L), "ROLE_USER");
        UserDTO dto = new UserDTO();
        dto.setUsername("john");
        dto.setFirstName("New");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(actor));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(target));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.update(dto, principal("alice"))
        );

        assertEquals(403, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    private static Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }

    private static User user(String username, Company company, String... roles) {
        User user = new User();
        user.setUsername(username);
        user.setCompany(company);
        for (String roleName : roles) {
            user.getRoles().add(new Role(roleName));
        }
        return user;
    }
}
