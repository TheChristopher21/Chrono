package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users") // Changed from /api/user to /api/users
public class UserController {
    @Autowired
    private final UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    public UserController(UserService userService) {
        this.userService = userService;
    }

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

    @GetMapping("/profile/{username}") // Changed to use PathVariable
    public UserDTO getProfile(@PathVariable String username) { // Changed from @RequestParam to @PathVariable
        User user = userService.getUserByUsername(username);
        return convertToDTO(user);
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setIsHourly(user.getIsHourly());
        dto.setIsPercentage(user.getIsPercentage());
        dto.setAnnualVacationDays(user.getAnnualVacationDays());
        dto.setWorkPercentage(user.getWorkPercentage());
        dto.setTrackingBalanceInMinutes(user.getTrackingBalanceInMinutes());
        if (user.getLastCustomer() != null) {
            dto.setLastCustomerId(user.getLastCustomer().getId());
            dto.setLastCustomerName(user.getLastCustomer().getName());
        }
        if (user.getCompany() != null) {
            dto.setCustomerTrackingEnabled(user.getCompany().getCustomerTrackingEnabled());
        }

        return dto;
    }
}
