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
// WICHTIG: Die @RequestMapping wird hier entfernt, damit jede Methode ihren eigenen, vollen Pfad haben kann.
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

    // KORRIGIERT: Voller Pfad für die Passwortänderung, wie vom Frontend aufgerufen.
    @PutMapping("/api/user/change-password")
    public ResponseEntity<String> changePassword(@RequestParam String username,
                                                 @RequestParam String currentPassword,
                                                 @RequestParam String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok("Password updated successfully");
    }

    // KORRIGIERT: Voller Pfad für das Profil, wie vom Frontend aufgerufen.
    @GetMapping("/api/users/profile/{username}")
    public UserDTO getProfile(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return convertToDTO(user);
    }

    @PutMapping("/api/user/update")
    public ResponseEntity<UserDTO> update(@RequestBody UserDTO dto) {
        User user = userRepository.findByUsername(dto.getUsername()).orElseThrow();

        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getAddress() != null) user.setAddress(dto.getAddress());
        if (dto.getBirthDate() != null) user.setBirthDate(dto.getBirthDate());
        if (dto.getEntryDate() != null) user.setEntryDate(dto.getEntryDate());
        if (dto.getCountry() != null) user.setCountry(dto.getCountry());
        if (dto.getTaxClass() != null) user.setTaxClass(dto.getTaxClass());
        if (dto.getTarifCode() != null) user.setTarifCode(dto.getTarifCode());
        if (dto.getCanton() != null) user.setCanton(dto.getCanton());
        if (dto.getCivilStatus() != null) user.setCivilStatus(dto.getCivilStatus());
        if (dto.getChildren() != null) user.setChildren(dto.getChildren());
        if (dto.getReligion() != null) user.setReligion(dto.getReligion());
        if (dto.getBankAccount() != null) user.setBankAccount(dto.getBankAccount());
        if (dto.getHealthInsurance() != null) user.setHealthInsurance(dto.getHealthInsurance());
        if (dto.getPersonnelNumber() != null) user.setPersonnelNumber(dto.getPersonnelNumber());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getMobilePhone() != null) user.setMobilePhone(dto.getMobilePhone());
        if (dto.getLandlinePhone() != null) user.setLandlinePhone(dto.getLandlinePhone());
        if (dto.getEmailNotifications() != null) user.setEmailNotifications(dto.getEmailNotifications());

        userRepository.save(user);
        return ResponseEntity.ok(convertToDTO(user));
    }

    @PostMapping("/api/users/{id}/opt-out")
    public ResponseEntity<Void> optOut(@PathVariable Long id) {
        User u = userRepository.findById(id).orElseThrow();
        u.setOptOut(true);
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/users/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        User u = userRepository.findById(id).orElseThrow();
        u.setDeleted(true);
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setAddress(user.getAddress());
        dto.setBirthDate(user.getBirthDate());
        dto.setEntryDate(user.getEntryDate());
        dto.setCountry(user.getCountry());
        dto.setTaxClass(user.getTaxClass());
        dto.setTarifCode(user.getTarifCode());
        dto.setCanton(user.getCanton());
        dto.setCivilStatus(user.getCivilStatus());
        dto.setChildren(user.getChildren());
        dto.setReligion(user.getReligion());
        dto.setHealthInsurance(user.getHealthInsurance());
        dto.setPersonnelNumber(user.getPersonnelNumber());
        dto.setEmail(user.getEmail());
        dto.setMobilePhone(user.getMobilePhone());
        dto.setLandlinePhone(user.getLandlinePhone());
        dto.setIsHourly(user.getIsHourly());
        dto.setIsPercentage(user.getIsPercentage());
        dto.setAnnualVacationDays(user.getAnnualVacationDays());
        dto.setWorkPercentage(user.getWorkPercentage());
        dto.setHourlyRate(user.getHourlyRate());
        dto.setBankAccount(user.getBankAccount());
        dto.setSocialSecurityNumber(user.getSocialSecurityNumber());
        dto.setTrackingBalanceInMinutes(user.getTrackingBalanceInMinutes());
        dto.setEmailNotifications(user.isEmailNotifications());
        if (user.getLastCustomer() != null) {
            dto.setLastCustomerId(user.getLastCustomer().getId());
            dto.setLastCustomerName(user.getLastCustomer().getName());
        }
        // Dieser Block ist der wichtige Teil des Konflikts. Er wird hier korrekt eingefügt.
        if (user.getCompany() != null) {
            dto.setCustomerTrackingEnabled(user.getCompany().getCustomerTrackingEnabled());
        }

        return dto;
    }
}