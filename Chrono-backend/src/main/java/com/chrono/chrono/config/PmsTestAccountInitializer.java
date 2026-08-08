package com.chrono.chrono.config;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional, environment-controlled bootstrap for the internal PMS test account.
 * The raw password is intentionally never stored in source control.
 */
@Component
@Order(100)
public class PmsTestAccountInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionService userPermissionService;

    @Value("${app.pms.test-account.enabled:false}")
    private boolean enabled;

    @Value("${app.pms.test-account.username:Christopher}")
    private String username;

    @Value("${app.pms.test-account.password:}")
    private String password;

    public PmsTestAccountInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder,
            UserPermissionService userPermissionService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.userPermissionService = userPermissionService;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (username == null || username.isBlank()) {
            System.out.println("[PmsTestAccountInitializer] PMS-Testzugang ist aktiviert, aber der Benutzername fehlt.");
            return;
        }

        var existingUser = userRepository.findByUsername(username.trim());
        if (existingUser.isEmpty() && (password == null || password.isBlank())) {
            System.out.println("[PmsTestAccountInitializer] Benutzer '" + username.trim()
                    + "' existiert nicht und kann ohne konfiguriertes Passwort nicht angelegt werden.");
            return;
        }

        User user = existingUser.orElseGet(this::createTestUser);

        boolean changed = false;
        if (user.getCompany() == null) {
            Company testCompany = companyRepository.findAll().stream().findFirst()
                    .orElseGet(() -> companyRepository.save(new Company("Chrono PMS Test")));
            user.setCompany(testCompany);
            changed = true;
        }
        if (password != null && !password.isBlank()
                && (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword()))) {
            user.setPassword(passwordEncoder.encode(password));
            changed = true;
        }

        Map<String, String> requestedPermissions = new HashMap<>(user.getPagePermissions());
        if (!UserPermissionService.ACCESS_MANAGE.equals(
                requestedPermissions.get(UserPermissionService.PAGE_PMS)
        )) {
            requestedPermissions.put(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE);
            user.setPagePermissions(
                    userPermissionService.resolvePermissionsForPersistence(user, requestedPermissions)
            );
            changed = true;
        }

        if (changed || user.getId() == null) {
            userRepository.save(user);
        }
        System.out.println("[PmsTestAccountInitializer] PMS-Testzugang fuer '" + user.getUsername() + "' ist bereit.");
    }

    private User createTestUser() {
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        User user = new User();
        user.setUsername(username.trim());
        user.setFirstName("Christopher");
        user.setLastName("PMS Test");
        user.setCountry("CH");
        user.setTarifCode("A0");
        user.setPersonnelNumber("PMS-TEST");
        user.setIncludeInTimeTracking(false);
        user.setAnnualVacationDays(0);
        user.setBreakDuration(0);
        user.getRoles().add(userRole);
        return user;
    }
}
