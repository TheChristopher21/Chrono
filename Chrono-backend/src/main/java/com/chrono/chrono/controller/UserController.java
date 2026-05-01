package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChangePasswordRequest;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
public class UserController {
    @Autowired
    private final UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPermissionService userPermissionService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/api/user/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        User actor = requireAuthenticatedUser(principal);
        if (!actor.getUsername().equals(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Passwort kann nur für das eigene Konto geändert werden.");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + request.getUsername()));

        String storedPassword = user.getPassword() != null ? user.getPassword() : user.getAdminPassword();

        try {
            if (storedPassword == null || !passwordEncoder.matches(request.getCurrentPassword(), storedPassword)) {
                return ResponseEntity.badRequest().body("Current password is incorrect");
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }

        String encodedNew = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedNew);

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ROLE_SUPERADMIN".equals(r.getRoleName()));
        if (isAdmin) {
            user.setAdminPassword(encodedNew);
        }

        userRepository.save(user);
        return ResponseEntity.ok("Password updated successfully");
    }

    @GetMapping("/api/users/profile/{username}")
    public UserDTO getProfile(@PathVariable String username, Principal principal) {
        User actor = requireAuthenticatedUser(principal);
        User user = userService.getUserByUsername(username);
        ensureCanViewProfile(actor, user);
        return convertToDTO(user);
    }

    @PutMapping("/api/user/update")
    public ResponseEntity<UserDTO> update(@RequestBody UserDTO dto, Principal principal) {
        User actor = requireAuthenticatedUser(principal);
        if (dto.getUsername() == null || !actor.getUsername().equals(dto.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profiländerungen sind nur für das eigene Konto erlaubt.");
        }

        User user = userRepository.findByUsername(dto.getUsername()).orElseThrow();

        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getAddress() != null) user.setAddress(dto.getAddress());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getMobilePhone() != null) user.setMobilePhone(dto.getMobilePhone());
        if (dto.getLandlinePhone() != null) user.setLandlinePhone(dto.getLandlinePhone());
        if (dto.getEmailNotifications() != null) user.setEmailNotifications(dto.getEmailNotifications());

        userRepository.save(user);
        return ResponseEntity.ok(convertToDTO(user));
    }

    @PostMapping("/api/users/{id}/opt-out")
    public ResponseEntity<Void> optOut(@PathVariable Long id, Principal principal) {
        User actor = requireAuthenticatedUser(principal);
        User target = userRepository.findById(id).orElseThrow();
        ensureCanManageUser(actor, target);
        target.setOptOut(true);
        userRepository.save(target);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/users/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id, Principal principal) {
        User actor = requireAuthenticatedUser(principal);
        User target = userRepository.findById(id).orElseThrow();
        ensureCanManageUser(actor, target);
        target.setDeleted(true);
        userRepository.save(target);
        return ResponseEntity.ok().build();
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO(user);
        dto.setPagePermissions(userPermissionService.resolvePagePermissions(user));
        return dto;
    }

    private User requireAuthenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }

        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + principal.getName()));
    }

    private void ensureCanViewProfile(User actor, User target) {
        if (actor.getUsername().equals(target.getUsername())) {
            return;
        }
        if (isSuperAdmin(actor)) {
            return;
        }
        if (isAdmin(actor) && belongsToSameCompany(actor, target)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf dieses Profil.");
    }

    private void ensureCanManageUser(User actor, User target) {
        if (isSuperAdmin(actor)) {
            return;
        }
        if (isAdmin(actor) && belongsToSameCompany(actor, target)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf diesen Benutzer.");
    }

    private boolean isAdmin(User actor) {
        return actor.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()));
    }

    private boolean isSuperAdmin(User actor) {
        return actor.getRoles().stream().anyMatch(r -> "ROLE_SUPERADMIN".equals(r.getRoleName()));
    }

    private boolean belongsToSameCompany(User actor, User target) {
        return actor.getCompany() != null
                && target.getCompany() != null
                && actor.getCompany().getId() != null
                && actor.getCompany().getId().equals(target.getCompany().getId());
    }
}
