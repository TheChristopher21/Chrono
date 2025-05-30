package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company; // Import wieder hinzugefügt
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // Fehlender Import für Map
import java.util.Optional; // Import für Optional
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository; // Injizieren Sie das CompanyRepository

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private VacationService vacationService;

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Logged in admin not found: " + principal.getName()));
        if (admin.getCompany() == null && !admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) { // SUPERADMIN darf auch ohne Firma alles sehen
            logger.warn("Admin {} has no company assigned and is not SUPERADMIN. Cannot list users.", principal.getName());
            // Für SUPERADMIN ohne Firma alle User zurückgeben, ansonsten leere Liste für regulären Admin ohne Firma
            if (admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                List<User> allUsersSystem = userRepository.findAll();
                List<UserDTO> dtosSystem = allUsersSystem.stream().map(UserDTO::new).collect(Collectors.toList());
                return ResponseEntity.ok(dtosSystem);
            }
            return ResponseEntity.ok(List.of());
        }

        List<User> users;
        if (admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) && admin.getCompany() == null) {
            users = userRepository.findAll(); // SUPERADMIN ohne Firma sieht alle User
        } else if (admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) && admin.getCompany() != null) {
            // SUPERADMIN with a company might see all users or just their company's users based on policy.
            // Assuming SUPERADMIN always sees all users regardless of their own company assignment for this example.
            users = userRepository.findAll();
        }
        else { // Regular Admin
            Long companyId = admin.getCompany().getId();
            users = userRepository.findByCompany_Id(companyId); // Regulärer Admin sieht User seiner Firma
        }


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

        Company targetCompany = null;
        // SUPERADMIN can assign a company if companyId is provided in DTO
        if (userDTO.getCompanyId() != null && admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            Optional<Company> companyOpt = companyRepository.findById(userDTO.getCompanyId());
            if (companyOpt.isPresent()) {
                targetCompany = companyOpt.get();
            } else {
                return ResponseEntity.badRequest().body("Specified companyId not found.");
            }
        } else if (admin.getCompany() != null) { // Regular admin assigns their own company
            targetCompany = admin.getCompany();
        } else if (!admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            // Regular admin MUST have a company to add a user to that company.
            return ResponseEntity.badRequest().body("Admin has no company assigned. Cannot add user.");
        }
        // If SUPERADMIN and no companyId in DTO, and SUPERADMIN has no company, user is created without company.


        if (userRepository.existsByUsername(userDTO.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required for new user");
        }

        User newUser = new User();
        newUser.setUsername(userDTO.getUsername());
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setEmail(userDTO.getEmail());
        newUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));

        if (targetCompany != null) {
            newUser.setCompany(targetCompany);
        }
        // If targetCompany is still null here, it means either:
        // 1. SUPERADMIN didn't provide a companyId and doesn't have one themselves.
        // 2. Some other logic error. User will be created without a company in this case for SUPERADMIN.


        String roleNameInput = (userDTO.getRoles() != null && !userDTO.getRoles().isEmpty()) ?
                userDTO.getRoles().get(0) : // Takes the first role from the list
                "ROLE_USER"; // Default role

        if (!roleNameInput.startsWith("ROLE_")) {
            roleNameInput = "ROLE_" + roleNameInput.toUpperCase();
        }
        final String finalRoleName = roleNameInput;

        // Security check for role assignment by non-SUPERADMIN
        if (!admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (finalRoleName.equals("ROLE_SUPERADMIN")) {
                return ResponseEntity.status(403).body("Regular admin cannot assign SUPERADMIN role.");
            }
            // A regular admin cannot assign ROLE_ADMIN to a user of a different company (though targetCompany logic above should align them)
            // or if the admin themselves is not a SUPERADMIN creating an admin in a company they don't belong to.
            if (finalRoleName.equals("ROLE_ADMIN") && (targetCompany == null || !targetCompany.getId().equals(admin.getCompany().getId()))) {
                if (admin.getCompany() == null || targetCompany == null || !targetCompany.getId().equals(admin.getCompany().getId())) {
                    return ResponseEntity.status(403).body("Regular admin cannot make user an ADMIN of a different company.");
                }
            }
        }


        Role userRole = roleRepository.findByRoleName(finalRoleName)
                .orElseGet(() -> roleRepository.save(new Role(finalRoleName)));
        newUser.getRoles().add(userRole);

        if (finalRoleName.equals("ROLE_ADMIN") || finalRoleName.equals("ROLE_SUPERADMIN")) {
            newUser.setAdminPassword(newUser.getPassword()); // Sets admin password same as user password
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
            newUser.setWorkPercentage(userDTO.getWorkPercentage() != null && userDTO.getWorkPercentage() >= 0 && userDTO.getWorkPercentage() <=100 ? userDTO.getWorkPercentage() : 100);
            Integer days = userDTO.getExpectedWorkDays();
            if (days == null || days < 1 || days > 7) {
                logger.warn("Admin {} tried to create percentage user {} with invalid expectedWorkDays: {}.", principal.getName(), userDTO.getUsername(), days);
                return ResponseEntity.badRequest().body("expectedWorkDays must be between 1 and 7 for percentage users.");
            }
            newUser.setExpectedWorkDays(days);
            newUser.setDailyWorkHours(null);
            newUser.setScheduleCycle(null);
            newUser.setWeeklySchedule(new ArrayList<>()); // Initialize to empty or default
            newUser.setScheduleEffectiveDate(null);
        } else if (isHourly) {
            newUser.setWorkPercentage(100);
            newUser.setDailyWorkHours(userDTO.getDailyWorkHours());
            newUser.setExpectedWorkDays(null);
            newUser.setScheduleCycle(null);
            newUser.setWeeklySchedule(new ArrayList<>());
            newUser.setScheduleEffectiveDate(null);
        } else { // Standard User
            newUser.setWorkPercentage(100);
            newUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : 8.5);
            Integer days = userDTO.getExpectedWorkDays();
            if (days != null && (days < 1 || days > 7)) {
                logger.warn("Admin {} tried to create standard user {} with invalid expectedWorkDays: {}.", principal.getName(), userDTO.getUsername(), days);
                return ResponseEntity.badRequest().body("If provided, expectedWorkDays must be between 1 and 7 for standard users.");
            }
            newUser.setExpectedWorkDays(days != null ? days : 5); // Default to 5 if not provided or invalid for standard user

            newUser.setScheduleCycle(userDTO.getScheduleCycle() != null && userDTO.getScheduleCycle() > 0 ? userDTO.getScheduleCycle() : 1);
            List<Map<String, Double>> schedule = userDTO.getWeeklySchedule();
            if (schedule == null || schedule.isEmpty()) {
                schedule = List.of(User.getDefaultWeeklyScheduleMap());
            }
            newUser.setWeeklySchedule(schedule);
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

        // SUPERADMIN can edit any user. Regular admin can only edit users in their own company.
        if (!adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (adminUser.getCompany() == null) {
                return ResponseEntity.status(403).body("Admin has no company and cannot update users.");
            }
            if (existingUser.getCompany() == null || !adminUser.getCompany().getId().equals(existingUser.getCompany().getId())) {
                logger.warn("Admin {} attempted to update user {} from a different company or user with no company.", adminUser.getUsername(), existingUser.getUsername());
                return ResponseEntity.status(403).body("Admin cannot update user from a different company or a user not assigned to any company.");
            }
        }
        // SUPERADMIN specific logic for changing user's company
        if (userDTO.getCompanyId() != null && adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (existingUser.getCompany() == null || !userDTO.getCompanyId().equals(existingUser.getCompany().getId())) {
                Company newCompany = companyRepository.findById(userDTO.getCompanyId())
                        .orElse(null);
                if (newCompany != null) {
                    existingUser.setCompany(newCompany);
                } else if (userDTO.getCompanyId() == 0L) { // Convention for unassigning company
                    existingUser.setCompany(null);
                }
                else {
                    return ResponseEntity.badRequest().body("Target companyId for user update not found.");
                }
            }
        } else if (userDTO.getCompanyId() != null && !adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            // Regular admin trying to change companyId - forbidden unless it's to their own company (which should match existingUser's company already)
            if (existingUser.getCompany() == null || !userDTO.getCompanyId().equals(existingUser.getCompany().getId()) || !userDTO.getCompanyId().equals(adminUser.getCompany().getId())) {
                return ResponseEntity.status(403).body("Regular admin cannot change the company of a user.");
            }
        }


        // Username change
        if (!existingUser.getUsername().equals(userDTO.getUsername())) {
            if (userRepository.existsByUsername(userDTO.getUsername())) {
                return ResponseEntity.badRequest().body("New username already exists: " + userDTO.getUsername());
            }
            existingUser.setUsername(userDTO.getUsername());
        }

        existingUser.setFirstName(userDTO.getFirstName());
        existingUser.setLastName(userDTO.getLastName());
        existingUser.setEmail(userDTO.getEmail());
        existingUser.setColor(userDTO.getColor());
        existingUser.setAnnualVacationDays(userDTO.getAnnualVacationDays() != null ? userDTO.getAnnualVacationDays() : existingUser.getAnnualVacationDays());
        existingUser.setBreakDuration(userDTO.getBreakDuration() != null ? userDTO.getBreakDuration() : existingUser.getBreakDuration());
        existingUser.setTrackingBalanceInMinutes(userDTO.getTrackingBalanceInMinutes() != null ? userDTO.getTrackingBalanceInMinutes() : existingUser.getTrackingBalanceInMinutes());


        if (newPassword != null && !newPassword.trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(newPassword));
            // If user is admin/superadmin, update adminPassword as well
            if (existingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                existingUser.setAdminPassword(existingUser.getPassword());
            }
        }

        if (userDTO.getRoles() != null && !userDTO.getRoles().isEmpty()) {
            String roleNameInput = userDTO.getRoles().get(0); // Assuming single role assignment for simplicity from DTO
            if (!roleNameInput.startsWith("ROLE_")) {
                roleNameInput = "ROLE_" + roleNameInput.toUpperCase();
            }
            final String finalRoleName = roleNameInput;

            if (!adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                if (finalRoleName.equals("ROLE_SUPERADMIN")) {
                    return ResponseEntity.status(403).body("Regular admin cannot assign SUPERADMIN role.");
                }
                // Prevent regular admin from escalating other users in *their own company* to ROLE_ADMIN if they aren't one themselves (edge case, typically admin role is for management)
                // Or changing a user in another company to ADMIN (already covered by company check)
            }

            Role dbRole = roleRepository.findByRoleName(finalRoleName)
                    .orElseGet(() -> roleRepository.save(new Role(finalRoleName)));
            existingUser.getRoles().clear();
            existingUser.getRoles().add(dbRole);
        }


        Boolean dtoIsHourly = userDTO.getIsHourly();
        Boolean dtoIsPercentage = userDTO.getIsPercentage();

        // Handle isPercentage first as it can affect isHourly
        if (dtoIsPercentage != null) {
            existingUser.setIsPercentage(dtoIsPercentage);
            if (Boolean.TRUE.equals(dtoIsPercentage)) {
                existingUser.setIsHourly(false); // Percentage users cannot be hourly
            }
        }
        // Then handle isHourly, only if not a percentage user
        if (dtoIsHourly != null && !Boolean.TRUE.equals(existingUser.getIsPercentage())) {
            existingUser.setIsHourly(dtoIsHourly);
        }


        if (Boolean.TRUE.equals(existingUser.getIsPercentage())) {
            Integer newWorkPercentage = userDTO.getWorkPercentage();
            existingUser.setWorkPercentage(newWorkPercentage != null && newWorkPercentage >= 0 && newWorkPercentage <= 100 ? newWorkPercentage : (existingUser.getWorkPercentage() != null ? existingUser.getWorkPercentage() : 100));

            Integer newExpectedWorkDays = userDTO.getExpectedWorkDays();
            if (newExpectedWorkDays != null) { // Only update if provided in DTO
                if (newExpectedWorkDays < 1 || newExpectedWorkDays > 7) {
                    logger.warn("Admin {} tried to update percentage user {} with invalid expectedWorkDays: {}", adminUser.getUsername(), existingUser.getUsername(), newExpectedWorkDays);
                    return ResponseEntity.badRequest().body("expectedWorkDays must be between 1 and 7 for percentage users.");
                }
                existingUser.setExpectedWorkDays(newExpectedWorkDays);
            } else if (existingUser.getExpectedWorkDays() == null) { // If currently null and not provided, set a default
                existingUser.setExpectedWorkDays(5); // Default for percentage if not set
            }
            // If DTO is null and existing is not null, keep existing value.

            existingUser.setDailyWorkHours(null);
            existingUser.setScheduleCycle(null);
            existingUser.setWeeklySchedule(new ArrayList<>());
            existingUser.setScheduleEffectiveDate(null);

        } else if (Boolean.TRUE.equals(existingUser.getIsHourly())) {
            existingUser.setWorkPercentage(100); // Hourly are effectively 100% of their worked time for pay.
            existingUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : existingUser.getDailyWorkHours());
            existingUser.setExpectedWorkDays(null);
            existingUser.setScheduleCycle(null);
            existingUser.setWeeklySchedule(new ArrayList<>());
            existingUser.setScheduleEffectiveDate(null);
        } else { // Standard User
            existingUser.setWorkPercentage(100);
            existingUser.setDailyWorkHours(userDTO.getDailyWorkHours() != null ? userDTO.getDailyWorkHours() : (existingUser.getDailyWorkHours() != null ? existingUser.getDailyWorkHours() : 8.5));

            Integer newExpectedWorkDays = userDTO.getExpectedWorkDays();
            if (newExpectedWorkDays != null) { // Only update if provided
                if (newExpectedWorkDays < 1 || newExpectedWorkDays > 7) {
                    logger.warn("Admin {} tried to update standard user {} with invalid expectedWorkDays: {}", adminUser.getUsername(), existingUser.getUsername(), newExpectedWorkDays);
                    return ResponseEntity.badRequest().body("If provided, expectedWorkDays must be between 1 and 7 for standard users.");
                }
                existingUser.setExpectedWorkDays(newExpectedWorkDays);
            } else if (existingUser.getExpectedWorkDays() == null) { // If currently null and not provided, set a default
                existingUser.setExpectedWorkDays(5); // Default for standard if not set
            }
            // If DTO is null and existing is not null, keep existing value.


            List<Map<String, Double>> schedule = userDTO.getWeeklySchedule();
            Integer cycle = userDTO.getScheduleCycle();
            LocalDate effectiveDate = userDTO.getScheduleEffectiveDate();

            if (schedule != null || cycle != null || effectiveDate != null) { // If any schedule part is in DTO
                existingUser.setWeeklySchedule(schedule != null && !schedule.isEmpty() ? schedule : (existingUser.getWeeklySchedule() != null ? existingUser.getWeeklySchedule() : List.of(User.getDefaultWeeklyScheduleMap())));
                existingUser.setScheduleCycle(cycle != null && cycle > 0 ? cycle : (existingUser.getScheduleCycle() != null ? existingUser.getScheduleCycle() : 1));
                existingUser.setScheduleEffectiveDate(effectiveDate != null ? effectiveDate : (existingUser.getScheduleEffectiveDate() != null ? existingUser.getScheduleEffectiveDate() : LocalDate.now()));
            }
            // If schedule DTO fields are all null, the existing schedule is preserved.
        }

        User updatedUser = userRepository.save(existingUser);

        try {
            timeTrackingService.rebuildUserBalance(updatedUser);
            logger.info("User balance rebuilt for {} after update by admin {}", updatedUser.getUsername(), adminUser.getUsername());
        } catch (Exception e) {
            logger.error("Error rebuilding balance for user {} after update: {}", updatedUser.getUsername(), e.getMessage(), e);
            // Decide if this should be a critical error blocking the response or just a logged warning.
            // For now, just log it.
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

        // Check if admin is SUPERADMIN or if they belong to the same company
        boolean isSuperAdmin = adminUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin) {
            if (adminUser.getCompany() == null ) {
                return ResponseEntity.status(403).body(Map.of("message", "Admin has no company assigned and cannot delete users."));
            }
            if (userToDelete.getCompany() == null || !adminUser.getCompany().getId().equals(userToDelete.getCompany().getId())) {
                logger.warn("Admin {} attempted to delete user {} from a different company.", adminUser.getUsername(), userToDelete.getUsername());
                return ResponseEntity.status(403).body(Map.of("message", "Admin cannot delete user from a different company."));
            }
        }
        // Prevent self-deletion
        if (adminUser.getId().equals(userToDelete.getId())) {
            logger.warn("Admin {} attempted to delete themselves.", adminUser.getUsername());
            return ResponseEntity.status(403).body(Map.of("message", "Admin cannot delete themselves."));
        }

        try {
            // Deleting associated data.
            vacationService.deleteVacationsByUser(userToDelete); //
            correctionRequestRepository.deleteByUser(userToDelete); //
            sickLeaveRepository.deleteByUser(userToDelete); // NEU: Krankmeldungen löschen
            // timeTrackingService.deleteTimeTrackingsByUser(userToDelete); // Falls implementiert

            userRepository.delete(userToDelete); //
            logger.info("Admin {} deleted user: {}", adminUser.getUsername(), userToDelete.getUsername()); //
            return ResponseEntity.ok(Map.of("message", "User deleted successfully")); //
        } catch (Exception ex) {
            logger.error("Error deleting user {} by admin {}: {}", userToDelete.getUsername(), adminUser.getUsername(), ex.getMessage(), ex); //
            return ResponseEntity.status(500).body(Map.of("message", "An error occurred while deleting the user and their associated data.")); //
        }
    }
}