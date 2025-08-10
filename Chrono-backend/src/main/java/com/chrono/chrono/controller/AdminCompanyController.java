package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.chrono.chrono.dto.CompanySettingsDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/company")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminCompanyController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    @GetMapping
    public ResponseEntity<?> getCompany(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        return ResponseEntity.ok(Map.of(
                "id", company.getId(),
                "name", company.getName(),
                "logoPath", company.getLogoPath()
        ));
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        return ResponseEntity.ok(CompanySettingsDTO.fromEntity(company));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody CompanySettingsDTO dto, Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        dto.applyToEntity(company);
        companyRepository.save(company);
        return ResponseEntity.ok(CompanySettingsDTO.fromEntity(company));
    }

    @PutMapping("/logo")
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file,
                                        Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        try {
            Path dir = Path.of("company-logos");
            Files.createDirectories(dir);
            String ext = Optional.ofNullable(file.getOriginalFilename())
                    .filter(n -> n.contains("."))
                    .map(n -> n.substring(n.lastIndexOf('.')))
                    .orElse(".png");
            Path target = dir.resolve("company-" + company.getId() + ext);
            file.transferTo(target);
            company.setLogoPath(target.toString());
            companyRepository.save(company);
            return ResponseEntity.ok(Map.of("logoPath", company.getLogoPath()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to save logo");
        }
    }
}
