package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company; // Import wieder hinzugefügt
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.VacationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map; // Fehlender Import für Map
import java.util.stream.Collectors;
// Optional wurde entfernt, da es nicht verwendet wird.

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private VacationService vacationService;

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Logged in admin not found: " + principal.getName()));
        if (admin.getCompany() == null) {
            logger.warn("Admin {} has no company assigned.", principal.getName());
            return ResponseEntity.ok(List.of());
        }

        Long companyId = admin.getCompany().getId();
        List<User> users = userRepository.findByCompany_Id(companyId);

        List<UserDTO> dtos = users.stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> addUser(@RequestBody UserDTO userDTO, Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Logged-in admin not found: " + principal.getName()));

        if (admin.getCompany() == null) {
            return ResponseEntity.badRequest().body("Admin has no company assigned");
        }

        if (userRepository.existsByUsername(userDTO.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        // Verwende getPassword() vom DTO, das jetzt existiert
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required for new user");
        }

        User newUser = new User();
        newUser.setUsername(userDTO.getUsername());
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setEmail(userDTO.getEmail());
        newUser.setPassword(passwordEncoder.encode(userDTO.getPassword())); // Password aus DTO holen
        newUser.setCompany(admin.getCompany());

        String roleNameInput = (userDTO.getRoles() != null && !userDTO.getRoles().isEmpty()) ?
                userDTO.getRoles().get(0) :
                "ROLE_USER";

        if (!roleNameInput.startsWith("ROLE_")) {
            roleNameInput = "ROLE_" + roleNameInput.toUpperCase();
        }
        final String finalRoleName = roleNameInput;
        Role userRole = roleRepository.findByRoleName(finalRoleName)
                .orElseGet(() -> roleRepository.save(new Role(finalRoleName)));
        newUser.getRoles().add(userRole);

        if (finalRoleName.equals("ROLE_ADMIN") || finalRoleName.equals("ROLE_SUPERADMIN")) {
            newUser.setAdminPassword(newUser.getPassword());
        }

        newUser.setTrackingBalanceInMinutes(userDTO.getTrackingBalanceInMinutes() != null ? userDTO.getTrackingBalanceInMinutes() : 0);
        newUser.setColor(userDTO.getColor() != null && !userDTO.getColor().isEmpty() ? userDTO.getColor() : "#CCCCCC");
        newUser.setAnnualVacationDays(userDTO.getAnnualVacationDays() != null ? userDTO.getAnnualVacationDays() : 25);
        newUser.setBreakDuration(userDTO.getBreakDuration() != null ? userDTO.getBreakDuration() : 30);

        boolean isHourly = userDTO.getIsHourly() != null ? userDTO.getIsHourly() : false;
        boolean isPercentage = userDTO.getIsPercentage() != null ? userDTO.getIsPercentage() : false;

        newUser.setIsHourly(isHourly);
        newUser.setIsPercentage(isPercentage);

        if (isPercentage) {
            newUser.setWorkPercentage(userDTO.getWorkPercentage() != null && userDTO.getWorkPercentage() > 0 && userDTO.getWorkPercentage() <=100 ? userDTO.getWorkPercentage() : 100);
            newUser.setDailyWorkHours(null);
            newUser.setExpectedWorkDays(null);
            newUser.setScheduleCycle(null);
            newUser.setWeeklySchedule(null);
            newUser.setScheduleEffectiveDate(null);
        } else if (isHourly) {
            newUser.setWorkPercentage(100); // Oder null, je nach Logik für Stundenzettel
            newUser.setDailyWorkHours(userDTO.getDailyWorkHours());
            newUser.setExpectedWorkDays(null);
            newUser.setScheduleCycle(null);
            newUser.setWeeklySchedule(null);
            newUser.setScheduleEffectiveDate(null);
        } else {
            newUser.setWorkPercentage(100); // Standard User hat 100% von seinem Schedule
            newUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : 8.5);
            newUser.setExpectedWorkDays(userDTO.getExpectedWorkDays() != null ? userDTO.getExpectedWorkDays() : 5.0);

            newUser.setScheduleCycle(userDTO.getScheduleCycle() != null && userDTO.getScheduleCycle() > 0 ? userDTO.getScheduleCycle() : 1);

            List<Map<String, Double>> schedule = userDTO.getWeeklySchedule();
            if (schedule == null || schedule.isEmpty()) {
                // Verwende die neue statische Methode aus der User-Entität
                schedule = List.of(User.getDefaultWeeklyScheduleMap());
            }
            newUser.setWeeklySchedule(schedule); // Typ ist jetzt List<Map<String, Double>>
            newUser.setScheduleEffectiveDate(userDTO.getScheduleEffectiveDate() != null ? userDTO.getScheduleEffectiveDate() : LocalDate.now());
        }

        User savedUser = userRepository.save(newUser);
        logger.info("Admin {} created new user: {}", principal.getName(), savedUser.getUsername());
        return ResponseEntity.ok(new UserDTO(savedUser));
    }


    @PutMapping
    @Transactional
    public ResponseEntity<?> updateUser(
            @RequestBody UserDTO userDTO,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            Principal principal
    ) {
        if (userDTO.getId() == null) {
            return ResponseEntity.badRequest().body("User ID is required for update");
        }

        User adminUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found: " + principal.getName()));

        User existingUser = userRepository.findById(userDTO.getId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userDTO.getId()));

        // Sicherheitscheck für Company-Zugehörigkeit
        if (adminUser.getCompany() == null || existingUser.getCompany() == null ||
                !adminUser.getCompany().getId().equals(existingUser.getCompany().getId())) {
            if (!adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                logger.warn("Admin {} attempted to update user {} from a different company.", adminUser.getUsername(), existingUser.getUsername());
                return ResponseEntity.status(403).body("Admin cannot update user from a different company.");
            }
        }

        // Basisdaten aktualisieren
        existingUser.setUsername(userDTO.getUsername()); // Beachte: Username sollte normalerweise nicht änderbar sein oder muss gesondert geprüft werden, ob er schon existiert.
        existingUser.setFirstName(userDTO.getFirstName());
        existingUser.setLastName(userDTO.getLastName());
        existingUser.setEmail(userDTO.getEmail());
        existingUser.setColor(userDTO.getColor());
        existingUser.setAnnualVacationDays(userDTO.getAnnualVacationDays() != null ? userDTO.getAnnualVacationDays() : existingUser.getAnnualVacationDays());
        existingUser.setBreakDuration(userDTO.getBreakDuration() != null ? userDTO.getBreakDuration() : existingUser.getBreakDuration());
        existingUser.setTrackingBalanceInMinutes(userDTO.getTrackingBalanceInMinutes() != null ? userDTO.getTrackingBalanceInMinutes() : existingUser.getTrackingBalanceInMinutes());


        // Passwort aktualisieren, falls ein neues gesendet wurde
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(newPassword));
            // Wenn der User Admin-Rollen hat, auch das Admin-Passwort synchronisieren
            if (existingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                existingUser.setAdminPassword(existingUser.getPassword());
            }
        }

        // Rollen aktualisieren
        if (userDTO.getRoles() != null && !userDTO.getRoles().isEmpty()) {
            String roleNameInput = userDTO.getRoles().get(0);
            if (!roleNameInput.startsWith("ROLE_")) {
                roleNameInput = "ROLE_" + roleNameInput.toUpperCase();
            }
            final String finalRoleName = roleNameInput;
            Role dbRole = roleRepository.findByRoleName(finalRoleName)
                    .orElseGet(() -> roleRepository.save(new Role(finalRoleName)));
            existingUser.getRoles().clear();
            existingUser.getRoles().add(dbRole);
        }

        // Arbeitsmodell-bezogene Felder aktualisieren (isHourly, isPercentage, etc.)
        // Hier ist die angepasste Logik:

        Boolean dtoIsHourly = userDTO.getIsHourly();
        Boolean dtoIsPercentage = userDTO.getIsPercentage();

        // Wenn isPercentage explizit im DTO gesetzt ist:
        if (dtoIsPercentage != null) {
            existingUser.setIsPercentage(dtoIsPercentage);
            if (Boolean.TRUE.equals(dtoIsPercentage)) { // Wenn User zu Percentage wechselt oder Percentage bleibt
                Integer newWorkPercentage = userDTO.getWorkPercentage();
                // Wenn DTO null, alten Wert nehmen oder Default
                if (newWorkPercentage == null) {
                    newWorkPercentage = existingUser.getWorkPercentage(); // Getter liefert Default 100, wenn null
                }
                existingUser.setWorkPercentage(newWorkPercentage > 0 && newWorkPercentage <= 100 ? newWorkPercentage : 100); // Stellt sicher, dass es im Bereich liegt

                // Explizit schedule-bezogene Felder nullen, da sie für Percentage-User nicht relevant sind
                existingUser.setDailyWorkHours(null);
                existingUser.setExpectedWorkDays(null);
                existingUser.setScheduleCycle(null);
                existingUser.setWeeklySchedule(null); // Setzt es auf eine leere Liste in der Entität, wenn `new ArrayList<>()` im Setter ist, oder null.
                existingUser.setScheduleEffectiveDate(null);
                existingUser.setIsHourly(false); // Percentage schließt Hourly aus
            }
        }

        // Wenn isHourly explizit im DTO gesetzt ist UND der User NICHT Percentage ist/wird:
        if (dtoIsHourly != null && !Boolean.TRUE.equals(existingUser.getIsPercentage())) {
            existingUser.setIsHourly(dtoIsHourly);
            if (Boolean.TRUE.equals(dtoIsHourly)) { // Wenn User zu Hourly wechselt oder Hourly bleibt (und nicht Percentage ist)
                existingUser.setWorkPercentage(100); // Stündliche sind effektiv 100% ihrer Arbeitszeit
                existingUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : existingUser.getDailyWorkHours()); // Behalte alten Wert oder setze neuen
                // Explizit schedule-bezogene Felder nullen
                existingUser.setExpectedWorkDays(null);
                existingUser.setScheduleCycle(null);
                existingUser.setWeeklySchedule(null);
                existingUser.setScheduleEffectiveDate(null);
            }
        }

        // Standard-User (weder Percentage noch Hourly explizit auf true gesetzt durch DTO)
        // Dieser Block wird ausgeführt, wenn der User explizit zu Standard gemacht wird (isPercentage=false, isHourly=false)
        // oder wenn die Flags im DTO null sind und der User vorher schon Standard war.
        if (!Boolean.TRUE.equals(existingUser.getIsPercentage()) && !Boolean.TRUE.equals(existingUser.getIsHourly())) {
            existingUser.setWorkPercentage(100); // Standard User = 100% (ihres Schedules)
            existingUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : (existingUser.getDailyWorkHours() != null ? existingUser.getDailyWorkHours() : 8.5));
            existingUser.setExpectedWorkDays(userDTO.getExpectedWorkDays() != null ? userDTO.getExpectedWorkDays() : (existingUser.getExpectedWorkDays() != null ? existingUser.getExpectedWorkDays() : 5.0));

            List<Map<String, Double>> schedule = userDTO.getWeeklySchedule();
            Integer cycle = userDTO.getScheduleCycle();

            // Nur aktualisieren, wenn neue Werte vom DTO kommen (nicht null und nicht leer für Liste)
            if (schedule != null && !schedule.isEmpty()) {
                existingUser.setWeeklySchedule(schedule);
                existingUser.setScheduleCycle(cycle != null && cycle > 0 ? cycle : (existingUser.getScheduleCycle() != null ? existingUser.getScheduleCycle() : 1));
                existingUser.setScheduleEffectiveDate(userDTO.getScheduleEffectiveDate() != null ? userDTO.getScheduleEffectiveDate() : (existingUser.getScheduleEffectiveDate() != null ? existingUser.getScheduleEffectiveDate() : LocalDate.now()));
            } else if (userDTO.getWeeklySchedule() == null && schedule == null) {
                // Explizit nullen, wenn DTO null sendet (und nicht nur eine leere Liste)
                // Dies ist wichtig, falls ein Standard-User seinen Schedule komplett entfernen soll (was unwahrscheinlich ist, aber der Vollständigkeit halber)
                // Oder wenn von Percentage/Hourly auf Standard gewechselt wird und keine Schedule-Daten mitkommen.
                existingUser.setWeeklySchedule(List.of(User.getDefaultWeeklyScheduleMap())); // Setzt einen Standard-Schedule
                existingUser.setScheduleCycle(1);
                existingUser.setScheduleEffectiveDate(LocalDate.now());
            }
            // Wenn schedule im DTO eine leere Liste ist, aber nicht null, bleibt der bestehende Schedule erhalten.
        }


        User updatedUser = userRepository.save(existingUser);

        // Saldo neu berechnen, falls arbeitsmodellrelevante Daten geändert wurden
        try {
            timeTrackingService.rebuildUserBalance(updatedUser);
            logger.info("User balance rebuilt for {} after update by admin {}", updatedUser.getUsername(), adminUser.getUsername());
        } catch (Exception e) {
            logger.error("Error rebuilding balance for user {} after update: {}", updatedUser.getUsername(), e.getMessage(), e);
            // Hier könnte man überlegen, ob der vorherige Save rückgängig gemacht werden soll, aber das ist komplexer.
            // Fürs Erste reicht ein Logging des Fehlers.
        }

        logger.info("Admin {} updated user: {}", adminUser.getUsername(), updatedUser.getUsername());
        return ResponseEntity.ok(new UserDTO(updatedUser));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Principal principal) {
        User adminUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found: " + principal.getName()));

        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        if (adminUser.getCompany() == null || userToDelete.getCompany() == null ||
                !adminUser.getCompany().getId().equals(userToDelete.getCompany().getId())) {
            if (!adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                logger.warn("Admin {} attempted to delete user {} from a different company.", adminUser.getUsername(), userToDelete.getUsername());
                return ResponseEntity.status(403).body("Admin cannot delete user from a different company.");
            }
        }
        if (adminUser.getId().equals(userToDelete.getId())) {
            logger.warn("Admin {} attempted to delete themselves.", adminUser.getUsername());
            return ResponseEntity.status(403).body("Admin cannot delete themselves.");
        }

        try {
            vacationService.deleteVacationsByUser(userToDelete);
            correctionRequestRepository.deleteByUser(userToDelete);

            // Annahme: TimeTracking wird über Cascade gelöscht oder muss hier manuell erfolgen
            // timeTrackingService.deleteTimeTrackingsByUser(userToDelete);
            // (Diese Methode müsste im TimeTrackingService implementiert werden)

            userRepository.delete(userToDelete);
            logger.info("Admin {} deleted user: {}", adminUser.getUsername(), userToDelete.getUsername());
            return ResponseEntity.ok("User deleted successfully");
        } catch (Exception ex) {
            logger.error("Error deleting user {} by admin {}: {}", userToDelete.getUsername(), adminUser.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.status(500).body("Error deleting user: " + ex.getMessage());
        }
    }
}