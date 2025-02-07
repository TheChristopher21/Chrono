package com.chrono.chrono.controller;

import com.chrono.chrono.dto.WorkConfigDTO;
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

    // GET all users – zurückgeben als DTO (ohne Passwort, dafür mit WorkConfig)
    @GetMapping
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(user -> new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                user.getExpectedWorkDays(),
                user.getDailyWorkHours(),
                user.getBreakDuration()
        )).collect(Collectors.toList());
    }

    // GET – Arbeitszeitkonfiguration eines Benutzers abrufen
    @GetMapping("/getWorkConfig/{userId}")
    public ResponseEntity<?> getWorkConfig(@PathVariable Long userId) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = optUser.get();
        WorkConfigDTO config = new WorkConfigDTO();
        config.setExpectedWorkDays(user.getExpectedWorkDays());
        config.setDailyWorkHours(user.getDailyWorkHours());
        config.setBreakDuration(user.getBreakDuration());
        return ResponseEntity.ok(config);
    }

    // PUT – Arbeitszeitkonfiguration aktualisieren
    @PutMapping("/updateWorkConfig/{userId}")
    public ResponseEntity<?> updateWorkConfig(@PathVariable Long userId, @RequestBody WorkConfigDTO config) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = optUser.get();
        user.setExpectedWorkDays(config.getExpectedWorkDays());
        user.setDailyWorkHours(config.getDailyWorkHours());
        user.setBreakDuration(config.getBreakDuration());
        userRepository.save(user);
        return ResponseEntity.ok("Work configuration updated");
    }

    // POST – Benutzer hinzufügen
    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            return ResponseEntity.badRequest().body("Password is required");
        }
        // Standardmäßig ROLE_USER setzen, falls keine Rolle angegeben wurde
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            Role role = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            user.getRoles().add(role);
        } else {
            // Nur den ersten Rollennamen übernehmen
            List<Role> roles = user.getRoles().stream().toList();
            user.getRoles().clear();
            Role role = roleRepository.findByRoleName(roles.get(0).getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(roles.get(0).getRoleName())));
            user.getRoles().add(role);
        }
        User saved = userRepository.save(user);
        return ResponseEntity.ok(new UserDTO(
                saved.getId(),
                saved.getUsername(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                saved.getExpectedWorkDays(),
                saved.getDailyWorkHours(),
                saved.getBreakDuration()
        ));
    }

    // PUT – Benutzer aktualisieren
    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        existing.setUsername(user.getUsername());
        existing.setFirstName(user.getFirstName());
        existing.setLastName(user.getLastName());
        existing.setEmail(user.getEmail());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            existing.getRoles().clear();
            String roleName = user.getRoles().iterator().next().getRoleName();
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> roleRepository.save(new Role(roleName)));
            existing.getRoles().add(role);
        }
        // Arbeitszeit-Konfiguration aktualisieren (falls vorhanden)
        if (user.getExpectedWorkDays() != null) {
            existing.setExpectedWorkDays(user.getExpectedWorkDays());
        }
        if (user.getDailyWorkHours() != null) {
            existing.setDailyWorkHours(user.getDailyWorkHours());
        }
        if (user.getBreakDuration() != null) {
            existing.setBreakDuration(user.getBreakDuration());
        }
        User updated = userRepository.save(existing);
        return ResponseEntity.ok(new UserDTO(
                updated.getId(),
                updated.getUsername(),
                updated.getFirstName(),
                updated.getLastName(),
                updated.getEmail(),
                updated.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                updated.getExpectedWorkDays(),
                updated.getDailyWorkHours(),
                updated.getBreakDuration()
        ));
    }

    // DELETE – Benutzer löschen
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    // DTO-Klasse, um User-Daten ohne Passwort zurückzugeben (inklusive WorkConfig)
    public static class UserDTO {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private List<String> roles;
        private Integer expectedWorkDays;
        private Double dailyWorkHours;
        private Integer breakDuration;

        public UserDTO(Long id, String username, String firstName, String lastName, String email, List<String> roles,
                       Integer expectedWorkDays, Double dailyWorkHours, Integer breakDuration) {
            this.id = id;
            this.username = username;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.roles = roles;
            this.expectedWorkDays = expectedWorkDays;
            this.dailyWorkHours = dailyWorkHours;
            this.breakDuration = breakDuration;
        }

        // Getter & Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public Integer getExpectedWorkDays() { return expectedWorkDays; }
        public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }
        public Double getDailyWorkHours() { return dailyWorkHours; }
        public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }
        public Integer getBreakDuration() { return breakDuration; }
        public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }
    }
}
