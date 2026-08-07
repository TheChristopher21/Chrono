package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.EmploymentModelType;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.EmploymentModelHistoryService;
import com.chrono.chrono.services.StripeService;
import com.chrono.chrono.services.UserPermissionService;
import com.stripe.model.PaymentIntent;
import com.chrono.chrono.utils.RegistrationFeatures;
// import com.chrono.chrono.utils.PasswordEncoderConfig; // Wird nicht direkt verwendet, PasswordEncoder reicht
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    @Autowired private UserPermissionService userPermissionService;
    @Autowired private EmploymentModelHistoryService employmentModelHistoryService;

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
    @Transactional
    public ResponseEntity<?> createCompanyWithAdmin(@RequestBody CreateCompanyWithAdminDTO body) {
        if (body.getCompanyName() == null || body.getCompanyName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Company name is required");
        }
        if (body.getAdminUsername() == null || body.getAdminUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Admin username is required");
        }
        if (body.getAdminPassword() == null || body.getAdminPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Admin password is required");
        }
        if (body.getAdminPassword().length() < 12) {
            return ResponseEntity.badRequest().body("Admin password must contain at least 12 characters");
        }
        if (body.getAdminPersonnelNumber() == null || body.getAdminPersonnelNumber().isBlank()) {
            return ResponseEntity.badRequest().body("Admin personnel number is required");
        }

        String country = normalizeCountry(body.getAdminCountry());
        if (country == null) {
            return ResponseEntity.badRequest().body("Admin country must be CH or DE");
        }
        if ("CH".equals(country) && trimToNull(body.getAdminTarifCode()) == null) {
            return ResponseEntity.badRequest().body("Admin tariff code is required for CH");
        }
        if ("DE".equals(country) && trimToNull(body.getAdminTaxClass()) == null) {
            return ResponseEntity.badRequest().body("Admin tax class is required for DE");
        }
        if (userRepository.existsByUsername(body.getAdminUsername().trim())) {
            return ResponseEntity.badRequest().body("Admin username already exists");
        }

        Company company = new Company();
        company.setName(body.getCompanyName().trim());
        company.setAddressLine1(body.getAddressLine1());
        company.setAddressLine2(body.getAddressLine2());
        company.setPostalCode(body.getPostalCode());
        company.setCity(body.getCity());
        company.setActive(true);
        if (body.getCantonAbbreviation() != null && !body.getCantonAbbreviation().trim().isEmpty()) {
            company.setCantonAbbreviation(body.getCantonAbbreviation().trim().toUpperCase());
        } else {
            company.setCantonAbbreviation(null);
        }
        company.setSlackWebhookUrl(body.getSlackWebhookUrl());
        company.setTeamsWebhookUrl(body.getTeamsWebhookUrl());
        company.setNotifyVacation(body.getNotifyVacation());
        company.setNotifyOvertime(body.getNotifyOvertime());
        company.setCustomerTrackingEnabled(body.getCustomerTrackingEnabled());
        company.setEnabledFeatures(RegistrationFeatures.sanitizeOptionalFeatures(body.getEnabledFeatures()));
        // Weitere Standardwerte für neue Firmen
        company.setPaid(false);
        company.setCanceled(false);
        company = companyRepository.save(company);

        User admin = new User();
        admin.setUsername(body.getAdminUsername().trim());
        String encodedPassword = passwordEncoder.encode(body.getAdminPassword());
        admin.setPassword(encodedPassword);
        admin.setAdminPassword(encodedPassword);
        admin.setEmail(trimToNull(body.getAdminEmail()));
        admin.setFirstName(trimToNull(body.getAdminFirstName()));
        admin.setLastName(trimToNull(body.getAdminLastName()));
        admin.setDepartment(trimToNull(body.getAdminDepartment()));
        admin.setCountry(country);
        admin.setTaxClass("DE".equals(country) ? trimToNull(body.getAdminTaxClass()) : null);
        admin.setTarifCode("CH".equals(country) ? trimToNull(body.getAdminTarifCode()) : null);
        admin.setCanton("CH".equals(country) ? trimToNull(body.getAdminCanton()) : null);
        admin.setPersonnelNumber(body.getAdminPersonnelNumber().trim());
        admin.setEmailNotifications(false);
        admin.setIncludeInTimeTracking(Boolean.TRUE.equals(body.getAdminIncludeInTimeTracking()));
        admin.setTrackingBalanceInMinutes(0);
        admin.setAnnualVacationDays(0);
        admin.setBreakDuration(0);
        admin.setIsHourly(false);
        admin.setIsPercentage(false);
        admin.setWorkPercentage(100);
        admin.setExpectedWorkDays(5);
        admin.setDailyWorkHours(8.5);
        admin.setScheduleCycle(1);
        admin.setWeeklySchedule(List.of(User.getDefaultWeeklyScheduleMap()));
        LocalDate today = employmentModelHistoryService.currentBerlinDate();
        admin.setEntryDate(today);
        admin.setScheduleEffectiveDate(today);
        admin.setCompany(company);

        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        admin.getRoles().add(adminRole);

        Map<String, String> requestedPermissions = new HashMap<>();
        if (Boolean.TRUE.equals(body.getAdminPmsAccess())) {
            requestedPermissions.put(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE);
        }
        admin.setPagePermissions(
                userPermissionService.resolvePermissionsForPersistence(admin, requestedPermissions)
        );

        User savedAdmin = userRepository.save(admin);
        employmentModelHistoryService.ensureBaselineEntry(savedAdmin, EmploymentModelType.STANDARD, today);
        company.getUsers().add(savedAdmin);

        Map<String,Object> response = new LinkedHashMap<>();
        response.put("company", CompanyDTO.fromEntity(company));
        response.put("adminUser", Map.of(
                "id", savedAdmin.getId(),
                "username", savedAdmin.getUsername(),
                "email", Optional.ofNullable(savedAdmin.getEmail()).orElse(""),
                "role", "ROLE_ADMIN",
                "pmsAccess", Boolean.TRUE.equals(body.getAdminPmsAccess())
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
        company.setAddressLine1(companyDTO.getAddressLine1());
        company.setAddressLine2(companyDTO.getAddressLine2());
        company.setPostalCode(companyDTO.getPostalCode());
        company.setCity(companyDTO.getCity());
        company.setActive(companyDTO.isActive()); // Standard auf true oder vom DTO nehmen
        company.setPaid(false); // Standard für neue Firmen
        company.setCanceled(false); // Standard für neue Firmen

        if (companyDTO.getCantonAbbreviation() != null && !companyDTO.getCantonAbbreviation().trim().isEmpty()) {
            company.setCantonAbbreviation(companyDTO.getCantonAbbreviation().trim().toUpperCase());
        } else {
            company.setCantonAbbreviation(null);
        }
        company.setSlackWebhookUrl(companyDTO.getSlackWebhookUrl());
        company.setTeamsWebhookUrl(companyDTO.getTeamsWebhookUrl());
        company.setNotifyVacation(companyDTO.getNotifyVacation());
        company.setNotifyOvertime(companyDTO.getNotifyOvertime());
        company.setCustomerTrackingEnabled(companyDTO.getCustomerTrackingEnabled());
        company.setEnabledFeatures(RegistrationFeatures.sanitizeOptionalFeatures(companyDTO.getEnabledFeatures()));

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
                    if (companyDTO.getAddressLine1() != null)
                        existingCompany.setAddressLine1(companyDTO.getAddressLine1());
                    if (companyDTO.getAddressLine2() != null)
                        existingCompany.setAddressLine2(companyDTO.getAddressLine2());
                    if (companyDTO.getPostalCode() != null)
                        existingCompany.setPostalCode(companyDTO.getPostalCode());
                    if (companyDTO.getCity() != null)
                        existingCompany.setCity(companyDTO.getCity());
                    // Das DTO sollte den aktuellen 'active' Status enthalten, nicht nur für den Toggle
                    existingCompany.setActive(companyDTO.isActive());

                    if (companyDTO.getCantonAbbreviation() != null) {
                        String canton = companyDTO.getCantonAbbreviation().trim().toUpperCase();
                        existingCompany.setCantonAbbreviation(canton.isEmpty() ? null : canton);
                    }
                    if (companyDTO.getSlackWebhookUrl() != null)
                        existingCompany.setSlackWebhookUrl(companyDTO.getSlackWebhookUrl());
                    if (companyDTO.getTeamsWebhookUrl() != null)
                        existingCompany.setTeamsWebhookUrl(companyDTO.getTeamsWebhookUrl());
                    if (companyDTO.getNotifyVacation() != null)
                        existingCompany.setNotifyVacation(companyDTO.getNotifyVacation());
                    if (companyDTO.getNotifyOvertime() != null)
                        existingCompany.setNotifyOvertime(companyDTO.getNotifyOvertime());
                    if (companyDTO.getCustomerTrackingEnabled() != null)
                        existingCompany.setCustomerTrackingEnabled(companyDTO.getCustomerTrackingEnabled());
                    if (companyDTO.getEnabledFeatures() != null)
                        existingCompany.setEnabledFeatures(RegistrationFeatures.sanitizeOptionalFeatures(companyDTO.getEnabledFeatures()));
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
        private String addressLine1;
        private String addressLine2;
        private String postalCode;
        private String city;
        private boolean active;
        private int    userCount;
        private boolean paid;
        private String  paymentMethod;
        private boolean canceled;
        private String cantonAbbreviation; // NEU
        private String slackWebhookUrl;
        private String teamsWebhookUrl;
        private Boolean notifyVacation;
        private Boolean notifyOvertime;
        private Boolean customerTrackingEnabled;
        private String logoPath;
        private Set<String> enabledFeatures;

        public static CompanyDTO fromEntity(Company co) {
            CompanyDTO dto = new CompanyDTO();
            dto.id = co.getId();
            dto.name = co.getName();
            dto.addressLine1 = co.getAddressLine1();
            dto.addressLine2 = co.getAddressLine2();
            dto.postalCode = co.getPostalCode();
            dto.city = co.getCity();
            dto.active = co.isActive();
            dto.userCount = co.getUsers() != null ? co.getUsers().size() : 0;
            dto.paid = co.isPaid();
            dto.paymentMethod = co.getPaymentMethod();
            dto.canceled = co.isCanceled();
            dto.cantonAbbreviation = co.getCantonAbbreviation(); // NEU
            dto.slackWebhookUrl = co.getSlackWebhookUrl();
            dto.teamsWebhookUrl = co.getTeamsWebhookUrl();
            dto.notifyVacation = co.getNotifyVacation();
            dto.notifyOvertime = co.getNotifyOvertime();
            dto.customerTrackingEnabled = co.getCustomerTrackingEnabled();
            dto.logoPath = co.getLogoPath();
            dto.enabledFeatures = RegistrationFeatures.sanitizeOptionalFeatures(co.getEnabledFeatures());
            return dto;
        }

        // Getter
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getAddressLine1() { return addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public String getPostalCode() { return postalCode; }
        public String getCity() { return city; }
        public boolean isActive() { return active; }
        public int getUserCount() { return userCount; }
        public boolean isPaid() { return paid; }
        public String getPaymentMethod() { return paymentMethod; }
        public boolean isCanceled() { return canceled; }
        public String getCantonAbbreviation() { return cantonAbbreviation; } // NEU
        public String getSlackWebhookUrl() { return slackWebhookUrl; }
        public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
        public Boolean getNotifyVacation() { return notifyVacation; }
        public Boolean getNotifyOvertime() { return notifyOvertime; }
        public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; }
        public String getLogoPath() { return logoPath; }
        public Set<String> getEnabledFeatures() { return enabledFeatures; }

        // Setter (wichtig für @RequestBody)
        public void setId(Long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public void setCity(String city) { this.city = city; }
        public void setActive(boolean active) { this.active = active; }
        public void setUserCount(int userCount) { this.userCount = userCount; }
        public void setPaid(boolean paid) { this.paid = paid; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
        public void setCantonAbbreviation(String cantonAbbreviation) { this.cantonAbbreviation = cantonAbbreviation; } // NEU
        public void setSlackWebhookUrl(String slackWebhookUrl) { this.slackWebhookUrl = slackWebhookUrl; }
        public void setTeamsWebhookUrl(String teamsWebhookUrl) { this.teamsWebhookUrl = teamsWebhookUrl; }
        public void setNotifyVacation(Boolean notifyVacation) { this.notifyVacation = notifyVacation; }
        public void setNotifyOvertime(Boolean notifyOvertime) { this.notifyOvertime = notifyOvertime; }
        public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; }
        public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
        public void setEnabledFeatures(Set<String> enabledFeatures) {
            this.enabledFeatures = (enabledFeatures != null)
                    ? RegistrationFeatures.sanitizeOptionalFeatures(enabledFeatures)
                    : null;
        }
    }

    public static class CreateCompanyWithAdminDTO {
        private String companyName;
        private String adminUsername;
        private String adminPassword;
        private String adminFirstName;
        private String adminLastName;
        private String adminEmail;
        private String adminDepartment;
        private String adminCountry;
        private String adminTaxClass;
        private String adminTarifCode;
        private String adminCanton;
        private String adminPersonnelNumber;
        private Boolean adminIncludeInTimeTracking;
        private Boolean adminPmsAccess;
        private String addressLine1;
        private String addressLine2;
        private String postalCode;
        private String city;
        private String cantonAbbreviation; // NEU
        private String slackWebhookUrl;
        private String teamsWebhookUrl;
        private Boolean notifyVacation;
        private Boolean notifyOvertime;
        private Boolean customerTrackingEnabled;
        private Set<String> enabledFeatures;

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
        public String getAdminDepartment() { return adminDepartment; }
        public void setAdminDepartment(String adminDepartment) { this.adminDepartment = adminDepartment; }
        public String getAdminCountry() { return adminCountry; }
        public void setAdminCountry(String adminCountry) { this.adminCountry = adminCountry; }
        public String getAdminTaxClass() { return adminTaxClass; }
        public void setAdminTaxClass(String adminTaxClass) { this.adminTaxClass = adminTaxClass; }
        public String getAdminTarifCode() { return adminTarifCode; }
        public void setAdminTarifCode(String adminTarifCode) { this.adminTarifCode = adminTarifCode; }
        public String getAdminCanton() { return adminCanton; }
        public void setAdminCanton(String adminCanton) { this.adminCanton = adminCanton; }
        public String getAdminPersonnelNumber() { return adminPersonnelNumber; }
        public void setAdminPersonnelNumber(String adminPersonnelNumber) { this.adminPersonnelNumber = adminPersonnelNumber; }
        public Boolean getAdminIncludeInTimeTracking() { return adminIncludeInTimeTracking; }
        public void setAdminIncludeInTimeTracking(Boolean adminIncludeInTimeTracking) { this.adminIncludeInTimeTracking = adminIncludeInTimeTracking; }
        public Boolean getAdminPmsAccess() { return adminPmsAccess; }
        public void setAdminPmsAccess(Boolean adminPmsAccess) { this.adminPmsAccess = adminPmsAccess; }
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getCantonAbbreviation() { return cantonAbbreviation; } // NEU
        public void setCantonAbbreviation(String cantonAbbreviation) { this.cantonAbbreviation = cantonAbbreviation; } // NEU
        public String getSlackWebhookUrl() { return slackWebhookUrl; }
        public void setSlackWebhookUrl(String slackWebhookUrl) { this.slackWebhookUrl = slackWebhookUrl; }
        public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
        public void setTeamsWebhookUrl(String teamsWebhookUrl) { this.teamsWebhookUrl = teamsWebhookUrl; }
        public Boolean getNotifyVacation() { return notifyVacation; }
        public void setNotifyVacation(Boolean notifyVacation) { this.notifyVacation = notifyVacation; }
        public Boolean getNotifyOvertime() { return notifyOvertime; }
        public void setNotifyOvertime(Boolean notifyOvertime) { this.notifyOvertime = notifyOvertime; }
        public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; }
        public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; }
        public Set<String> getEnabledFeatures() { return enabledFeatures; }
        public void setEnabledFeatures(Set<String> enabledFeatures) {
            this.enabledFeatures = (enabledFeatures != null)
                    ? RegistrationFeatures.sanitizeOptionalFeatures(enabledFeatures)
                    : null;
        }
    }

    private static String normalizeCountry(String rawCountry) {
        String country = trimToNull(rawCountry);
        if (country == null) {
            return null;
        }
        country = country.toUpperCase(Locale.ROOT);
        return Set.of("CH", "DE").contains(country) ? country : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
