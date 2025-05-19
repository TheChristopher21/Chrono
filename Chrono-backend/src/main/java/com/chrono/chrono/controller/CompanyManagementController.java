package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.utils.PasswordEncoderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
// import jakarta.validation.constraints.NotBlank; // auskommentiert: Validation-Bibliothek fehlt
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Alle End-Points, die nur der SUPERADMIN benutzen darf,
 * um Firmen (Mandanten) und deren Admin-Accounts zu verwalten.
 */
@RestController
@RequestMapping("/api/superadmin/companies")
@PreAuthorize("hasRole('SUPERADMIN')")
public class CompanyManagementController {

    // Repositories
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private RoleRepository    roleRepository;
    @Autowired private PasswordEncoder   passwordEncoder;

    // ============ (1) Alle Firmen laden ============
    @GetMapping
    public List<CompanyDTO> getAllCompanies() {
        return companyRepository.findAll()
                .stream()
                .map(CompanyDTO::fromEntity)
                .toList();
    }

    // ============ (2) Ein einzelne Firma nach ID ============
    @GetMapping("/{id}")
    public ResponseEntity<?> getCompany(@PathVariable Long id) {
        var opt = companyRepository.findById(id);
        if (opt.isPresent()) {
            return ResponseEntity.ok(opt.get()); // → Company
        } else {
            return ResponseEntity.badRequest().body("Company not found"); // → String
        }
    }

    // ============ (3) Neue Firma in EINEM Schritt + Admin anlegen ============
    @PostMapping("/create-with-admin")
    public ResponseEntity<?> createCompanyWithAdmin(@RequestBody CreateCompanyWithAdminDTO body) {

        // 1) Einfache Checks (statt @NotBlank, da du keine Validation-Bibliothek hast)
        if (body.getCompanyName() == null || body.getCompanyName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Company name is required");
        }
        if (body.getAdminUsername() == null || body.getAdminUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Admin username is required");
        }
        if (body.getAdminPassword() == null || body.getAdminPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Admin password is required");
        }
        // 2) Prüfen, ob User existiert
        if (userRepository.existsByUsername(body.getAdminUsername())) {
            return ResponseEntity.badRequest().body("Admin username already exists");
        }

        // 3) Neue Firma
        Company company = new Company();
        company.setName(body.getCompanyName().trim());
        company.setActive(true);
        company = companyRepository.save(company);
        company.setPaid(false);
        company.setCanceled(false);
        // 4) Admin-User anlegen
        User admin = new User();
        admin.setUsername(body.getAdminUsername().trim());
        admin.setPassword(passwordEncoder.encode(body.getAdminPassword()));
        admin.setEmail(body.getAdminEmail());
        admin.setFirstName(body.getAdminFirstName());
        admin.setLastName(body.getAdminLastName());
        admin.setCompany(company);

        // Rolle "ROLE_ADMIN"
        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        admin.getRoles().add(adminRole);

        userRepository.save(admin);

        // 5) Antwort
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("company", CompanyDTO.fromEntity(company));
        response.put("adminUser", Map.of(
                "id", admin.getId(),
                "username", admin.getUsername(),
                "email", admin.getEmail()
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============ (4) Firma separat anlegen ============
    @PostMapping
    public ResponseEntity<?> createCompany(@RequestBody Company body) {
        if (body.getName() == null || body.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Firma-Name ist erforderlich");
        }
        // ID muss null sein
        body.setId(null);
        body.setActive(true);
        body.setPaid(false);
        body.setCanceled(false);
        Company saved = companyRepository.save(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CompanyDTO.fromEntity(saved));
    }

    // ============ (5) Firma bearbeiten ============
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable Long id, @RequestBody Company body) {
        return companyRepository.findById(id)
                // Hier erzwingen wir den gemeinsamen Rückgabetyp "ResponseEntity<?>"
                .<ResponseEntity<?>>map(existing -> {
                    if (body.getName() != null && !body.getName().trim().isEmpty()) {
                        existing.setName(body.getName());
                    }
                    if (body.isActive() != existing.isActive()) {
                        existing.setActive(body.isActive());
                    }
                    if (body.isPaid() != existing.isPaid()) {
                        existing.setPaid(body.isPaid());
                    }
                    if (body.getPaymentMethod() != null) {
                        existing.setPaymentMethod(body.getPaymentMethod());
                    }
                    if (body.isCanceled() != existing.isCanceled()) {
                        existing.setCanceled(body.isCanceled());
                    }
                    companyRepository.save(existing);
                    return ResponseEntity.ok(existing);

                })
                // Falls nichts gefunden, company not found → BAD_REQUEST
                .orElseGet(() -> ResponseEntity.badRequest().body("Company not found"));
    }

    // ============ (5b) Zahlungsstatus aktualisieren ============
    @PutMapping("/{id}/payment")
    public ResponseEntity<CompanyDTO> updatePayment(@PathVariable Long id,
                                                    @RequestBody PaymentUpdateDTO dto) {
        return companyRepository.findById(id)
                .map(co -> {
                    if (dto.getPaymentMethod() != null)
                        co.setPaymentMethod(dto.getPaymentMethod());
                    if (dto.getPaid() != null)
                        co.setPaid(dto.getPaid());
                    if (dto.getCanceled() != null)
                        co.setCanceled(dto.getCanceled());

                    companyRepository.save(co);
                    return ResponseEntity.ok(CompanyDTO.fromEntity(co));
                })
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }



    // ============ (6) Firma löschen ============
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable Long id) {
        return companyRepository.findById(id)
                .map(co -> {
                    if (!co.getUsers().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("Company still has users – remove them first.");
                    }
                    companyRepository.delete(co);
                    return ResponseEntity.ok("Company deleted");
                })
                .orElseGet(() -> ResponseEntity.badRequest().body("Company not found"));
    }

    // ----------------------------------------------------------------------
    //   DTO-KLASSEN
    // ----------------------------------------------------------------------

    /** Kurzes DTO für die Firmenliste. */
    public static class CompanyDTO {
        private Long   id;
        private String name;
        private boolean active;
        private int    userCount;
        private boolean paid;
        private String  paymentMethod;
        private boolean canceled;

        public static CompanyDTO fromEntity(Company co) {
            CompanyDTO dto = new CompanyDTO();
            dto.id        = co.getId();
            dto.name      = co.getName();
            dto.active    = co.isActive();
            dto.userCount     = co.getUsers().size();
            dto.paid          = co.isPaid();
            dto.paymentMethod = co.getPaymentMethod();
            dto.canceled      = co.isCanceled();
            return dto;
        }

        // Getter/Setter
        public Long getId() { return id; }
        public String getName() { return name; }
        public boolean isActive() { return active; }
        public int getUserCount() { return userCount; }
        public boolean isPaid() { return paid; }
        public String getPaymentMethod() { return paymentMethod; }
        public boolean isCanceled() { return canceled; }

        public void setId(Long i) { this.id = i; }
        public void setName(String n) { this.name = n; }
        public void setActive(boolean a) { this.active = a; }
        public void setUserCount(int u) { this.userCount = u; }
        public void setPaid(boolean p) { this.paid = p; }
        public void setPaymentMethod(String pm) { this.paymentMethod = pm; }
        public void setCanceled(boolean c) { this.canceled = c; }
    }

    /**
     * Request-Body für „Firma + Admin“
     * Wenn du keine Validierung-Bibliothek hast,
     * bitte @NotBlank etc. entfernen oder auskommentieren.
     */
    public static class CreateCompanyWithAdminDTO {
        private String companyName;
        private String adminUsername;
        private String adminPassword;
        private String adminFirstName;
        private String adminLastName;
        private String adminEmail;

        // --- Getter/Setter ---
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getAdminUsername() { return adminUsername; }
        public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        public String getAdminFirstName() { return adminFirstName; }
        public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
        public String getAdminLastName() { return adminLastName; }
        public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    }

    /** DTO für updatePayment */
    public static class PaymentUpdateDTO {
        private Boolean paid;
        private String  paymentMethod;
        private Boolean canceled;

        public Boolean getPaid() { return paid; }
        public void setPaid(Boolean paid) { this.paid = paid; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public Boolean getCanceled() { return canceled; }
        public void setCanceled(Boolean canceled) { this.canceled = canceled; }
    }
}
