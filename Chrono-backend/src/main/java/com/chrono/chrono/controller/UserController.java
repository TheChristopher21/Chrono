package com.chrono.chrono.controller;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestParam String username,
                                                 @RequestParam String currentPassword,
                                                 @RequestParam String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Überprüfe, ob das aktuelle Passwort korrekt ist
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }
        // Hash das neue Passwort und speichere es
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok("Password updated successfully");
    }
}
