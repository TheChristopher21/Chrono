package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.utils.RegistrationFeatures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/registration-features")
public class RegistrationFeatureController {

    @Autowired
    private CompanyRepository companyRepository;

    @GetMapping
    public ResponseEntity<?> getRegistrationFeatures(@RequestParam(value = "companyId", required = false) Long companyId) {
        if (companyId == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("enabledFeatures", List.copyOf(RegistrationFeatures.OPTIONAL_FEATURES));
            return ResponseEntity.ok(response);
        }

        return companyRepository.findById(companyId)
                .map(company -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("enabledFeatures",
                            List.copyOf(RegistrationFeatures.sanitizeOptionalFeatures(company.getEnabledFeatures())));
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Map<String, Object>>body(Map.of("message", "Company not found")));
    }
}
