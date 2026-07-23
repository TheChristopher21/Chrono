package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.HolidayService;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.chrono.chrono.dto.CompanySettingsDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/company")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN') or hasRole('PAYROLL_ADMIN')")
public class AdminCompanyController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private HolidayService holidayService;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping
    public ResponseEntity<?> getCompany(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", company.getId());
        body.put("name", company.getName());
        body.put("logoPath", company.getLogoPath());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        return ResponseEntity.ok(CompanySettingsDTO.fromEntity(
                company,
                holidayService.getCompanyHolidayPreferences(company)
        ));
    }

    @GetMapping("/holiday-catalog")
    public ResponseEntity<?> getHolidayCatalog() {
        return ResponseEntity.ok(holidayService.getHolidayCatalog());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody CompanySettingsDTO dto, Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Admin has no company");
        }
        Company company = admin.getCompany();
        boolean holidaySettingsChanged = dto.getHolidayPreferences() != null || dto.getCustomHolidaySelectionEnabled() != null;
        if (dto.getHolidayPreferences() != null && dto.getCustomHolidaySelectionEnabled() == null) {
            dto.setCustomHolidaySelectionEnabled(true);
        }
        dto.applyToEntity(company);
        companyRepository.save(company);
        if (dto.getHolidayPreferences() != null) {
            holidayService.replaceCompanyHolidayPreferences(company, dto.getHolidayPreferences());
        }
        if (holidaySettingsChanged) {
            List<User> companyUsers = userRepository.findByCompany_IdAndDeletedFalse(company.getId());
            for (User user : companyUsers) {
                timeTrackingService.rebuildUserBalance(user);
            }
        }
        return ResponseEntity.ok(CompanySettingsDTO.fromEntity(
                company,
                holidayService.getCompanyHolidayPreferences(company)
        ));
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
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            company.setLogoPath(target.toString());
            companyRepository.save(company);
            return ResponseEntity.ok(Map.of("logoPath", company.getLogoPath()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to save logo");
        }
    }
}
