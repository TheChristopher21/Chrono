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

/**
 * Rest-Controller für ADMIN-Operationen rund um User
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * GET /api/admin/users
     * Alle User zurückgeben
     */
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
                        user.getBreakDuration()
                ))
                .collect(Collectors.toList());
    }

    /**
     * POST /api/admin/users
     * Neuen User anlegen
     */
    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User userBody) {
        // Prüfen, ob Username schon existiert
        if (userRepository.existsByUsername(userBody.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        // Passwort encoden
        if (userBody.getPassword() != null && !userBody.getPassword().isEmpty()) {
            userBody.setPassword(passwordEncoder.encode(userBody.getPassword()));
        } else {
            return ResponseEntity.badRequest().body("Password is required");
        }
        // Rolle
        if (userBody.getRoles() == null || userBody.getRoles().isEmpty()) {
            // Standard: ROLE_USER
            Role defaultRole = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            userBody.getRoles().add(defaultRole);
        } else {
            // Falls nur eine Rolle mitgegeben wird, holen wir uns das Role-Objekt
            // (oder erstellen es bei Bedarf)
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            userBody.getRoles().clear();
            userBody.getRoles().add(dbRole);
        }
        // expectedWorkDays, dailyWorkHours, breakDuration sind bereits im userBody gesetzt
        User saved = userRepository.save(userBody);

        // Antwort mit UserDTO
        UserDTO dto = new UserDTO(
                saved.getId(),
                saved.getUsername(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                saved.getExpectedWorkDays(),
                saved.getDailyWorkHours(),
                saved.getBreakDuration()
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * PUT /api/admin/users
     * Vorhandenen User aktualisieren
     */
    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User userBody) {
        if (userBody.getId() == null) {
            return ResponseEntity.badRequest().body("User ID is required for update");
        }
        // User holen
        User existing = userRepository.findById(userBody.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        existing.setUsername(userBody.getUsername());
        existing.setFirstName(userBody.getFirstName());
        existing.setLastName(userBody.getLastName());
        existing.setEmail(userBody.getEmail());

        // Password nur ändern, wenn mitgegeben
        if (userBody.getPassword() != null && !userBody.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(userBody.getPassword()));
        }
        // Rollen
        if (userBody.getRoles() != null && !userBody.getRoles().isEmpty()) {
            // Nur eine Rolle annehmen
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));

            existing.getRoles().clear();
            existing.getRoles().add(dbRole);
        }

        // Arbeitszeiten
        if (userBody.getExpectedWorkDays() != null) {
            existing.setExpectedWorkDays(userBody.getExpectedWorkDays());
        }
        if (userBody.getDailyWorkHours() != null) {
            existing.setDailyWorkHours(userBody.getDailyWorkHours());
        }
        if (userBody.getBreakDuration() != null) {
            existing.setBreakDuration(userBody.getBreakDuration());
        }

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
                updated.getBreakDuration()
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * DELETE /api/admin/users/{id}
     * Benutzer löschen
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    /**
     * Optional: GET /api/admin/users/getWorkConfig/{userId}
     * Falls du separat die WorkConfig abfragen willst
     */
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

    /**
     * Optional: PUT /api/admin/users/updateWorkConfig/{userId}
     * Falls du separat die WorkConfig updaten willst
     */
    @PutMapping("/updateWorkConfig/{userId}")
    public ResponseEntity<?> updateWorkConfig(@PathVariable Long userId,
                                              @RequestBody UserDTO dto) {
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
