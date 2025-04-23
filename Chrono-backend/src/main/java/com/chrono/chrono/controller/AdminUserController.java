package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.services.VacationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VacationRequestRepository vacationRequestRepository;

    @Autowired
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private VacationService vacationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userRepository.findAll();

        // Anstatt die Felder von Hand zu mappen, verwenden wir den UserDTO(User)‐Konstruktor,
        // der isPercentage + workPercentage bereits enthält.
        List<UserDTO> dtos = users.stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User userBody) {
        // Username-Check
        if (userRepository.existsByUsername(userBody.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        // Passwort muss vorhanden sein
        if (userBody.getPassword() == null || userBody.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        // Passwörter hashen
        String hashed = passwordEncoder.encode(userBody.getPassword());
        userBody.setPassword(hashed);

        // Falls ROLE_ADMIN => AdminPassword = hashed
        if (userBody.getRoles() != null &&
                userBody.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
            userBody.setAdminPassword(hashed);
        }

        if (userBody.getTrackingBalanceInMinutes() == null) {
            userBody.setTrackingBalanceInMinutes(0);
        }

        // Rollen
        if (userBody.getRoles() == null || userBody.getRoles().isEmpty()) {
            Role defaultRole = roleRepository.findByRoleName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
            userBody.getRoles().add(defaultRole);
        } else {
            // Wir nehmen nur die erste Rolle
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            userBody.getRoles().clear();
            userBody.getRoles().add(dbRole);
        }

        // scheduleEffectiveDate
        userBody.setScheduleEffectiveDate(LocalDate.now());

        // Speichern
        User saved = userRepository.save(userBody);

        // Ausgabe mit dem umfangreichen DTO:
        UserDTO dto = new UserDTO(saved);
        return ResponseEntity.ok(dto);
    }

    @PutMapping
    public ResponseEntity<?> updateUser(
            @RequestBody User userBody,
            @RequestParam(value = "currentPassword", required = false) String currentPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword
    ) {
        if (userBody.getId() == null) {
            return ResponseEntity.badRequest().body("User ID is required for update");
        }
        Optional<User> optionalUser = userRepository.findById(userBody.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User existing = optionalUser.get();

        // Passwort-Update
        if (newPassword != null && !newPassword.isEmpty()) {
            // currentPassword prüfen
            if (currentPassword == null || currentPassword.trim().isEmpty()
                    || !passwordEncoder.matches(currentPassword, existing.getPassword())) {
                return ResponseEntity.status(403).body("Incorrect password");
            }
            String hashed = passwordEncoder.encode(newPassword);
            existing.setPassword(hashed);
            if (existing.getRoles() != null &&
                    existing.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                existing.setAdminPassword(hashed);
            }
            if (userBody.getTrackingBalanceInMinutes() == null) {
                existing.setTrackingBalanceInMinutes(0);
            } else {
                existing.setTrackingBalanceInMinutes(userBody.getTrackingBalanceInMinutes());
            }

        } else {
            // Falls currentPassword angegeben => check
            if (currentPassword != null && !currentPassword.trim().isEmpty()) {
                if (!passwordEncoder.matches(currentPassword, existing.getPassword())) {
                    return ResponseEntity.status(403).body("Incorrect password");
                }
            }
        }

        // Basis-Felder
        existing.setUsername(userBody.getUsername());
        existing.setFirstName(userBody.getFirstName());
        existing.setLastName(userBody.getLastName());
        existing.setEmail(userBody.getEmail());

        // Rollen
        if (userBody.getRoles() != null && !userBody.getRoles().isEmpty()) {
            Role inputRole = userBody.getRoles().iterator().next();
            Role dbRole = roleRepository.findByRoleName(inputRole.getRoleName())
                    .orElseGet(() -> roleRepository.save(new Role(inputRole.getRoleName())));
            existing.getRoles().clear();
            existing.getRoles().add(dbRole);
        }

        // Arbeits-Zeit-Felder
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

        // isHourly
        if (userBody.getIsHourly() != null) {
            existing.setIsHourly(userBody.getIsHourly());
        }

        // isPercentage, workPercentage
        if (userBody.getIsPercentage() != null) {
            existing.setIsPercentage(userBody.getIsPercentage());
        }
        if (userBody.getWorkPercentage() != null) {
            existing.setWorkPercentage(userBody.getWorkPercentage());
        }

        // Speichern
        User updated = userRepository.save(existing);

        // Ausgabe via UserDTO - enthält nun auch isPercentage / workPercentage
        UserDTO dto = new UserDTO(updated);
        return ResponseEntity.ok(dto);
    }

    /**
     * Löscht den Benutzer inkl. Urlaubsanträgen und Korrekturanträgen
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        // Vacation-Requests löschen
        try {
            vacationService.deleteVacationsByUser(user);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Fehler beim Löschen der Urlaubseinträge: " + ex.getMessage());
        }

        // CorrectionRequests löschen
        try {
            correctionRequestRepository.deleteByUser(user);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Fehler beim Löschen der Korrekturanträge: " + ex.getMessage());
        }

        // User löschen
        try {
            userRepository.delete(user);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Fehler beim Löschen des Benutzers: " + ex.getMessage());
        }
        return ResponseEntity.ok("User deleted successfully");
    }
}
