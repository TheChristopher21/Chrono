// src/main/java/com/chrono/chrono/controller/AdminUserController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    public ResponseEntity<?> getAllUsers() {
        var users = userRepository.findAll();
        var dtos = users.stream()
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                        user.getExpectedWorkDays(),
                        user.getDailyWorkHours(),
                        user.getBreakDuration(),
                        user.getColor(),
                        user.getScheduleCycle(),
                        user.getWeeklySchedule(),
                        user.getScheduleEffectiveDate(),
                        user.getIsHourly(),
                        user.getAnnualVacationDays()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User userBody) {
        if (userRepository.existsByUsername(userBody.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (userBody.getPassword() != null && !userBody.getPassword().isEmpty()) {
            String hashed = passwordEncoder.encode(userBody.getPassword());
            userBody.setPassword(hashed);
            if (userBody.getRoles() != null && userBody.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                userBody.setAdminPassword(hashed);
            }
        } else {
            return ResponseEntity.badRequest().body("Password is required");
        }
        if (userBody.getRoles() == null || userBody.getRoles().isEmpty()) {
            var defaultRole = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            userBody.getRoles().add(defaultRole);
        } else {
            var inputRole = userBody.getRoles().iterator().next();
            var dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            userBody.getRoles().clear();
            userBody.getRoles().add(dbRole);
        }
        userBody.setScheduleEffectiveDate(LocalDate.now());
        User saved = userRepository.save(userBody);
        var dto = new UserDTO(
                saved.getId(),
                saved.getUsername(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                saved.getExpectedWorkDays(),
                saved.getDailyWorkHours(),
                saved.getBreakDuration(),
                saved.getColor(),
                saved.getScheduleCycle(),
                saved.getWeeklySchedule(),
                saved.getScheduleEffectiveDate(),
                saved.getIsHourly(),
                saved.getAnnualVacationDays()
        );
        return ResponseEntity.ok(dto);
    }

    @PutMapping
    public ResponseEntity<?> updateUser(
            @RequestBody User userBody,
            @RequestParam(value = "currentPassword", required = false) String currentPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword) {

        if (userBody.getId() == null) {
            return ResponseEntity.badRequest().body("User ID is required for update");
        }
        Optional<User> optionalUser = userRepository.findById(userBody.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User existing = optionalUser.get();

        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || currentPassword.trim().isEmpty()
                    || !passwordEncoder.matches(currentPassword, existing.getPassword())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password");
            }
            String hashed = passwordEncoder.encode(newPassword);
            existing.setPassword(hashed);
            if (existing.getRoles() != null && existing.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                existing.setAdminPassword(hashed);
            }
        } else {
            if (currentPassword != null && !currentPassword.trim().isEmpty()) {
                if (!passwordEncoder.matches(currentPassword, existing.getPassword())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password");
                }
            }
        }

        existing.setUsername(userBody.getUsername());
        existing.setFirstName(userBody.getFirstName());
        existing.setLastName(userBody.getLastName());
        existing.setEmail(userBody.getEmail());

        if (userBody.getRoles() != null && !userBody.getRoles().isEmpty()) {
            var inputRole = userBody.getRoles().iterator().next();
            var dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
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
        if (userBody.getAnnualVacationDays() != null) {
            existing.setAnnualVacationDays(userBody.getAnnualVacationDays());
        }

        existing.setColor(userBody.getColor());

        if (userBody.getScheduleCycle() != null) {
            existing.setScheduleCycle(userBody.getScheduleCycle());
        }
        if (userBody.getWeeklySchedule() != null) {
            existing.setWeeklySchedule(userBody.getWeeklySchedule());
            existing.setScheduleEffectiveDate(LocalDate.now());
        }
        if (userBody.getIsHourly() != null) {
            existing.setIsHourly(userBody.getIsHourly());
        }

        User updated = userRepository.save(existing);
        var dto = new UserDTO(
                updated.getId(),
                updated.getUsername(),
                updated.getFirstName(),
                updated.getLastName(),
                updated.getEmail(),
                updated.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()),
                updated.getExpectedWorkDays(),
                updated.getDailyWorkHours(),
                updated.getBreakDuration(),
                updated.getColor(),
                updated.getScheduleCycle(),
                updated.getWeeklySchedule(),
                updated.getScheduleEffectiveDate(),
                updated.getIsHourly(),
                updated.getAnnualVacationDays()
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
        var opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User u = opt.get();
        return ResponseEntity.ok(
                "ExpectedWorkDays=" + u.getExpectedWorkDays() +
                        ", dailyWorkHours=" + u.getDailyWorkHours() +
                        ", breakDuration=" + u.getBreakDuration() +
                        ", scheduleCycle=" + u.getScheduleCycle() +
                        ", weeklySchedule=" + u.getWeeklySchedule() +
                        ", scheduleEffectiveDate=" + u.getScheduleEffectiveDate() +
                        ", isHourly=" + u.getIsHourly() +
                        ", annualVacationDays=" + u.getAnnualVacationDays()
        );
    }

    @PutMapping("/updateWorkConfig/{userId}")
    public ResponseEntity<?> updateWorkConfig(@PathVariable Long userId, @RequestBody UserDTO dto) {
        var opt = userRepository.findById(userId);
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
        if (dto.getScheduleCycle() != null) {
            user.setScheduleCycle(dto.getScheduleCycle());
        }
        if (dto.getWeeklySchedule() != null) {
            user.setWeeklySchedule(dto.getWeeklySchedule());
            user.setScheduleEffectiveDate(LocalDate.now());
        }
        if (dto.getIsHourly() != null) {
            user.setIsHourly(dto.getIsHourly());
        }
        userRepository.save(user);
        return ResponseEntity.ok("Work configuration updated");
    }
}
