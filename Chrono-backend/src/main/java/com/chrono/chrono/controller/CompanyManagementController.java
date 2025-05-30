package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.StripeService;
import com.stripe.model.PaymentIntent;
// import com.chrono.chrono.utils.PasswordEncoderConfig; // Wird nicht direkt verwendet, PasswordEncoder reicht
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/superadmin/companies")
@PreAuthorize("hasRole('SUPERADMIN')")
public class CompanyManagementController {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private RoleRepository    roleRepository;
    @Autowired private PasswordEncoder   passwordEncoder;
    @Autowired private StripeService     stripeService;

    @GetMapping
    public List<CompanyDTO> getAllCompanies() {
        return companyRepository.findAll()
                .stream()
                .map(CompanyDTO::fromEntity)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCompany(@PathVariable Long id) {
        return companyRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().body("Company not found"));
    }

    @PostMapping("/create-with-admin")
    public ResponseEntity<?> createCompanyWithAdmin(@RequestBody CreateCompanyWithAdminDTO body) {
        if (body.getCompanyName() == null || body.getCompanyName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Company name is required");
        }
        if (body.getAdminUsername() == null || body.getAdminUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Admin username is required");
        }
        if (body.getAdminPassword() == null || body.getAdminPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Admin password is required");
        }
        if (userRepository.existsByUsername(body.getAdminUsername().trim())) {
            return ResponseEntity.badRequest().body("Admin username already exists");
        }

        Company company = new Company();
        company.setName(body.getCompanyName().trim());
        company.setActive(true);
        if (body.getCantonAbbreviation() != null && !body.getCantonAbbreviation().trim().isEmpty()) {
            company.setCantonAbbreviation(body.getCantonAbbreviation().trim().toUpperCase());
        } else {
            company.setCantonAbbreviation(null);
        }
        // Weitere Standardwerte für neue Firmen
        company.setPaid(false);
        company.setCanceled(false);
        company = companyRepository.save(company);

        User admin = new User();
        admin.setUsername(body.getAdminUsername().trim());
        admin.setPassword(passwordEncoder.encode(body.getAdminPassword()));
        admin.setEmail(body.getAdminEmail()); // Kann null sein
        admin.setFirstName(body.getAdminFirstName()); // Kann null sein
        admin.setLastName(body.getAdminLastName()); // Kann null sein
        admin.setCompany(company);

        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        admin.getRoles().add(adminRole);
        userRepository.save(admin);

        Map<String,Object> response = new LinkedHashMap<>();
        response.put("company", CompanyDTO.fromEntity(company));
        response.put("adminUser", Map.of(
                "id", admin.getId(),
                "username", admin.getUsername(),
                "email", Optional.ofNullable(admin.getEmail()).orElse("")
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    public ResponseEntity<?> createCompany(@RequestBody CompanyDTO companyDTO) { // Akzeptiere DTO für Konsistenz
        if (companyDTO.getName() == null || companyDTO.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Firmenname ist erforderlich");
        }
        Company company = new Company();
        company.setId(null); // Sicherstellen, dass es eine neue Entität ist
        company.setName(companyDTO.getName().trim());
        company.setActive(companyDTO.isActive()); // Standard auf true oder vom DTO nehmen
        company.setPaid(false); // Standard für neue Firmen
        company.setCanceled(false); // Standard für neue Firmen

        if (companyDTO.getCantonAbbreviation() != null && !companyDTO.getCantonAbbreviation().trim().isEmpty()) {
            company.setCantonAbbreviation(companyDTO.getCantonAbbreviation().trim().toUpperCase());
        } else {
            company.setCantonAbbreviation(null);
        }

        Company saved = companyRepository.save(company);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CompanyDTO.fromEntity(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable Long id, @RequestBody CompanyDTO companyDTO) { // Akzeptiere DTO
        return companyRepository.findById(id)
                .<ResponseEntity<?>>map(existingCompany -> {
                    if (companyDTO.getName() != null && !companyDTO.getName().trim().isEmpty()) {
                        existingCompany.setName(companyDTO.getName().trim());
                    }
                    // Das DTO sollte den aktuellen 'active' Status enthalten, nicht nur für den Toggle
                    existingCompany.setActive(companyDTO.isActive());

                    if (companyDTO.getCantonAbbreviation() != null) {
                        String canton = companyDTO.getCantonAbbreviation().trim().toUpperCase();
                        existingCompany.setCantonAbbreviation(canton.isEmpty() ? null : canton);
                    }
                    // Zahlungsstatus sollte über /payment aktualisiert werden, um die Logik getrennt zu halten
                    // existingCompany.setPaid(companyDTO.isPaid());
                    // existingCompany.setPaymentMethod(companyDTO.getPaymentMethod());
                    // existingCompany.setCanceled(companyDTO.isCanceled());

                    companyRepository.save(existingCompany);
                    return ResponseEntity.ok(CompanyDTO.fromEntity(existingCompany));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body("Company not found"));
    }

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
                .orElseGet(() -> ResponseEntity.notFound().build()); // Besser: Not Found
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<?> getPaymentsForCompany(@PathVariable Long id) {
        try {
            List<PaymentIntent> payments = stripeService.listPaymentsForCompany(id);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch payments: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable Long id) {
        return companyRepository.findById(id)
                .map(co -> {
                    if (!co.getUsers().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("Company still has users – remove them first.");
                    }
                    companyRepository.delete(co);
                    return ResponseEntity.ok(Map.of("message", "Company deleted successfully")); // JSON-Antwort
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Company not found"));
    }

    public static class CompanyDTO {
        private Long   id;
        private String name;
        private boolean active;
        private int    userCount;
        private boolean paid;
        private String  paymentMethod;
        private boolean canceled;
        private String cantonAbbreviation; // NEU

        public static CompanyDTO fromEntity(Company co) {
            CompanyDTO dto = new CompanyDTO();
            dto.id = co.getId();
            dto.name = co.getName();
            dto.active = co.isActive();
            dto.userCount = co.getUsers() != null ? co.getUsers().size() : 0;
            dto.paid = co.isPaid();
            dto.paymentMethod = co.getPaymentMethod();
            dto.canceled = co.isCanceled();
            dto.cantonAbbreviation = co.getCantonAbbreviation(); // NEU
            return dto;
        }

        // Getter
        public Long getId() { return id; }
        public String getName() { return name; }
        public boolean isActive() { return active; }
        public int getUserCount() { return userCount; }
        public boolean isPaid() { return paid; }
        public String getPaymentMethod() { return paymentMethod; }
        public boolean isCanceled() { return canceled; }
        public String getCantonAbbreviation() { return cantonAbbreviation; } // NEU

        // Setter (wichtig für @RequestBody)
        public void setId(Long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setActive(boolean active) { this.active = active; }
        public void setUserCount(int userCount) { this.userCount = userCount; }
        public void setPaid(boolean paid) { this.paid = paid; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
        public void setCantonAbbreviation(String cantonAbbreviation) { this.cantonAbbreviation = cantonAbbreviation; } // NEU
    }

    public static class CreateCompanyWithAdminDTO {
        private String companyName;
        private String adminUsername;
        private String adminPassword;
        private String adminFirstName;
        private String adminLastName;
        private String adminEmail;
        private String cantonAbbreviation; // NEU

        // Getter/Setter
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
        public String getCantonAbbreviation() { return cantonAbbreviation; } // NEU
        public void setCantonAbbreviation(String cantonAbbreviation) { this.cantonAbbreviation = cantonAbbreviation; } // NEU
    }

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