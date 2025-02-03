// src/main/java/com/chrono/chrono/controller/AdminUserController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    // GET all users – mapping to DTOs (ohne Passwort)
    @GetMapping
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(user -> new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        )).collect(Collectors.toList());
    }

    // POST – Neuen User hinzufügen
    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User user) {
        if(userRepository.findByUsername(user.getUsername()).isPresent()){
            return ResponseEntity.badRequest().body("Username already exists");
        }
        // Passwort muss vorhanden sein und wird gehasht
        if(user.getPassword() != null && !user.getPassword().isEmpty()){
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            return ResponseEntity.badRequest().body("Password is required");
        }
        // Wenn keine Rolle gesetzt ist, wird standardmäßig ROLE_USER verwendet
        if(user.getRoles() == null || user.getRoles().isEmpty()){
            Role role = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            user.getRoles().add(role);
        } else {
            // Falls Rollen gesetzt sind, nutze nur die erste (Einzelauswahl)
            // Lösche alle und setze nur den ersten
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
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        ));
    }

    // PUT – User aktualisieren
    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        existing.setUsername(user.getUsername());
        existing.setFirstName(user.getFirstName());
        existing.setLastName(user.getLastName());
        existing.setEmail(user.getEmail());
        // Passwort nur aktualisieren, wenn ein neuer Wert angegeben wurde
        if(user.getPassword() != null && !user.getPassword().isEmpty()){
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        // Aktualisiere die Rolle – erwarte, dass nur eine Rolle (als String) angegeben wird
        if(user.getRoles() != null && !user.getRoles().isEmpty()){
            existing.getRoles().clear();
            // Nehme den ersten Rollennamen aus der Liste
            String roleName = user.getRoles().iterator().next().getRoleName();
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> roleRepository.save(new Role(roleName)));
            existing.getRoles().add(role);
        }
        User updated = userRepository.save(existing);
        return ResponseEntity.ok(new UserDTO(
                updated.getId(),
                updated.getUsername(),
                updated.getFirstName(),
                updated.getLastName(),
                updated.getEmail(),
                updated.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        ));
    }

    // DELETE – User löschen
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if(!userRepository.existsById(id)){
            return ResponseEntity.badRequest().body("User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    // DTO-Klasse, um User-Daten ohne Passwort zurückzugeben
    public static class UserDTO {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private List<String> roles;

        public UserDTO(Long id, String username, String firstName, String lastName, String email, List<String> roles) {
            this.id = id;
            this.username = username;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.roles = roles;
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
    }
}
