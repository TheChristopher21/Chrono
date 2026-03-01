package com.chrono.chrono.config;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Default auf true, falls keine Umgebungsvariable gesetzt ist
    @Value("${app.initialize.admin:true}")
    private boolean initializeAdmin;

    // Default-Werte: "admin" für Username und Passwort
    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!initializeAdmin) {
            System.out.println("[DataInitializer] Admin-Initialisierung ist deaktiviert (app.initialize.admin=false).");
            return;
        }

        if (adminUsername.trim().isEmpty() || adminPassword.trim().isEmpty()) {
            System.out.println("[DataInitializer] Admin-Zugangsdaten sind leer, Admin-Konto wird nicht erstellt.");
            return;
        }

        Optional<User> adminUserOptional = userRepository.findByUsername(adminUsername);
        if (adminUserOptional.isEmpty()) {
            // Admin-Konto anlegen
            User adminUser = new User();
            adminUser.setUsername(adminUsername);
            String encodedPassword = passwordEncoder.encode(adminPassword);
            adminUser.setPassword(encodedPassword);
            adminUser.setAdminPassword(encodedPassword);
            adminUser.setFirstName("Default");
            adminUser.setLastName("Admin");
            adminUser.setEmail("admin@example.com");

            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
            Role payrollRole = roleRepository.findByRoleName("ROLE_PAYROLL_ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_PAYROLL_ADMIN")));
            adminUser.getRoles().add(adminRole);
            adminUser.getRoles().add(payrollRole);

            userRepository.save(adminUser);
            System.out.println("[DataInitializer] Admin-Konto ('" + adminUsername + "') wurde erstellt.");
        } else {
            System.out.println("[DataInitializer] Admin-Konto existiert bereits (Benutzername: '" + adminUsername + "').");
        }
    }
}
