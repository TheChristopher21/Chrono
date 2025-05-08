package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // BEISPIEL: POST /api/superadmin/users/createAdminForCompany
    // Erwartet z. B. ein JSON:
    // {
    //    "username": "adminFirm2",
    //    "password": "somePass",
    //    "roles": [ { "roleName": "ROLE_ADMIN" } ],
    //    "companyId": 2
    // }
    @PostMapping("/users/createAdminForCompany")
    public ResponseEntity<?> createAdminForCompany(@RequestBody CreateAdminDTO dto) {
        // 1) Firma laden
        Optional<Company> optCo = companyRepository.findById(dto.getCompanyId());
        if (optCo.isEmpty()) {
            return ResponseEntity.badRequest().body("Company not found");
        }
        Company company = optCo.get();

        // 2) User anlegen
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username is required");
        }
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        User newUser = new User();
        newUser.setUsername(dto.getUsername().trim());
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        newUser.setCompany(company);

        // Rolle ADMIN oder SUPERADMIN, was du willst:
        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        newUser.getRoles().add(adminRole);

        userRepository.save(newUser);

        return ResponseEntity.ok(newUser);  // oder DTO
    }

    // BEISPIEL: GET /api/superadmin/companies/{id}/users
    // Gibt Liste von Usern zurück (z. B. als Minimale Felder oder als DTO)
    @GetMapping("/companies/{companyId}/users")
    public ResponseEntity<?> getUsersInCompany(@PathVariable Long companyId) {
        Optional<Company> optCo = companyRepository.findById(companyId);
        if (optCo.isEmpty()) {
            return ResponseEntity.badRequest().body("Company not found");
        }
        Company company = optCo.get();
        Set<User> userSet = company.getUsers();

        // Du kannst z. B. eine einfache DTO-Liste basteln:
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : userSet) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            // Liste der Rollen
            map.put("roles", u.getRoles().stream().map(Role::getRoleName).toList());
            // optional: ...
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // OPTIONAL: Export-Funktion
    // GET /api/superadmin/companies/{id}/export
    @GetMapping("/companies/{companyId}/export")
    public ResponseEntity<?> exportCompany(@PathVariable Long companyId) {
        Optional<Company> optCo = companyRepository.findById(companyId);
        if (optCo.isEmpty()) {
            return ResponseEntity.badRequest().body("Company not found");
        }

        Company co = optCo.get();
        // Baue dir z. B. CSV:
        StringBuilder sb = new StringBuilder();
        sb.append("UserID;Username;Roles\n");
        for (User u : co.getUsers()) {
            String roles = u.getRoles().stream().map(Role::getRoleName).reduce((a,b)->a+","+b).orElse("");
            sb.append(u.getId()).append(";")
                    .append(u.getUsername()).append(";")
                    .append(roles).append("\n");
        }
        // Ausliefern
        byte[] csvBytes = sb.toString().getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN); // oder MEDIA_TYPE_OCTET_STREAM
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("company_"+co.getName()+".csv")
                .build());
        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    // DTO-Klasse für createAdminForCompany
    public static class CreateAdminDTO {
        private String username;
        private String password;
        private List<Map<String,String>> roles;
        private Long companyId;

        public CreateAdminDTO() {}

        // getters+setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public List<Map<String,String>> getRoles() { return roles; }
        public void setRoles(List<Map<String,String>> roles) { this.roles = roles; }
        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
    }
}
