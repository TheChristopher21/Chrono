package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getRoles().stream()
                                .map(Role::getRoleName)
                                .collect(Collectors.toList()),
                        user.getExpectedWorkDays(),
                        user.getDailyWorkHours(),
                        user.getBreakDuration(),
                        user.getColor()
                ))
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User userBody) {
        if (userRepository.existsByUsername(userBody.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (userBody.getPassword() != null && !userBody.getPassword().isEmpty()) {
            // Hash das normale Passwort und setze auch das Admin-Passwort, falls der User ein Admin ist.
            String hashed = passwordEncoder.encode(userBody.getPassword());
            userBody.setPassword(hashed);
            if (userBody.getRoles() != null && userBody.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                userBody.setAdminPassword(hashed);
            }
        } else {
            return ResponseEntity.badRequest().body("Password is required");
        }
        if (userBody.getRoles() == null || userBody.getRoles().isEmpty()) {
            Role defaultRole = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            userBody.getRoles().add(defaultRole);
        } else {
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            userBody.getRoles().clear();
            userBody.getRoles().add(dbRole);
        }
        User saved = userRepository.save(userBody);
        UserDTO dto = new UserDTO(
                saved.getId(),
                saved.getUsername(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                saved.getExpectedWorkDays(),
                saved.getDailyWorkHours(),
                saved.getBreakDuration(),
                saved.getColor()
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * Update-Endpoint: Aktualisiert einen Benutzer.
     * Erwartet:
     * - JSON-Body mit den User-Daten (ohne Passwortfelder)
     * - Request-Parameter "currentPassword" und "newPassword" für die Passwortänderung
     */
    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User userBody,
                                        @RequestParam(value = "currentPassword", required = false) String currentPassword,
                                        @RequestParam(value = "newPassword", required = false) String newPassword) {
        if (userBody.getId() == null) {
            return ResponseEntity.badRequest().body("User ID is required for update");
        }
        User existing = userRepository.findById(userBody.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        existing.setUsername(userBody.getUsername());
        existing.setFirstName(userBody.getFirstName());
        existing.setLastName(userBody.getLastName());
        existing.setEmail(userBody.getEmail());
        // Falls ein neues Passwort gesetzt werden soll, muss das aktuelle Passwort stimmen
        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, existing.getPassword())) {
                return ResponseEntity.badRequest().body("Current password is incorrect");
            }
            String hashed = passwordEncoder.encode(newPassword);
            existing.setPassword(hashed);
            // Falls der User Admin ist, aktualisiere auch das Admin-Passwort
            if (existing.getRoles() != null && existing.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                existing.setAdminPassword(hashed);
            }
        }
        if (userBody.getRoles() != null && !userBody.getRoles().isEmpty()) {
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            existing.getRoles().clear();
            existing.getRoles().add(dbRole);
        }
        if (userBody.getExpectedWorkDays() != null) {
            existing.setExpectedWorkDays(userBody.getExpectedWorkDays());
        }
        if (userBody.getDailyWorkHours() != null) {
            existing.setDailyWorkHours(userBody.getDailyWorkHours());
        }
        if (userBody.getBreakDuration() != null) {
            existing.setBreakDuration(userBody.getBreakDuration());
        }
        existing.setColor(userBody.getColor());
        User updated = userRepository.save(existing);
        UserDTO dto = new UserDTO(
                updated.getId(),
                updated.getUsername(),
                updated.getFirstName(),
                updated.getLastName(),
                updated.getEmail(),
                updated.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                updated.getExpectedWorkDays(),
                updated.getDailyWorkHours(),
                updated.getBreakDuration(),
                updated.getColor()
        );
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    @GetMapping("/getWorkConfig/{userId}")
    public ResponseEntity<?> getWorkConfig(@PathVariable Long userId) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User u = opt.get();
        return ResponseEntity.ok(
                "ExpectedWorkDays=" + u.getExpectedWorkDays() +
                        ", dailyWorkHours=" + u.getDailyWorkHours() +
                        ", breakDuration=" + u.getBreakDuration()
        );
    }

    @PutMapping("/updateWorkConfig/{userId}")
    public ResponseEntity<?> updateWorkConfig(@PathVariable Long userId, @RequestBody UserDTO dto) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = opt.get();
        if (dto.getExpectedWorkDays() != null) {
            user.setExpectedWorkDays(dto.getExpectedWorkDays());
        }
        if (dto.getDailyWorkHours() != null) {
            user.setDailyWorkHours(dto.getDailyWorkHours());
        }
        if (dto.getBreakDuration() != null) {
            user.setBreakDuration(dto.getBreakDuration());
        }
        userRepository.save(user);
        return ResponseEntity.ok("Work configuration updated");
    }
}
