// src/main/java/com/chrono/chrono/config/DataInitializer.java
package com.chrono.chrono.config;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public void run(String... args) throws Exception {
        // Prüfe, ob ein Benutzer mit dem Benutzernamen "admin" existiert
        Optional<User> adminUserOptional = userRepository.findByUsername("admin");
        if (adminUserOptional.isEmpty()) {
            // Erstelle neuen Admin-Benutzer
            User adminUser = new User();
            adminUser.setUsername("admin");
            // Setze sowohl das Login-Passwort als auch das separate Admin-Passwort (falls verwendet)
            String encodedPassword = passwordEncoder.encode("admin");
            adminUser.setPassword(encodedPassword);
            adminUser.setAdminPassword(encodedPassword);
            adminUser.setFirstName("Default");
            adminUser.setLastName("Admin");
            adminUser.setEmail("admin@example.com");

            // Setze die Admin-Rolle
            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
            adminUser.getRoles().add(adminRole);

            userRepository.save(adminUser);
            System.out.println("[DataInitializer] Default admin user created.");
        } else {
            System.out.println("[DataInitializer] Admin user already exists.");
        }
    }
}
