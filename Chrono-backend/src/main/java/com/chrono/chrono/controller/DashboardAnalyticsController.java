package com.chrono.chrono.controller;

import com.chrono.chrono.services.DashboardAnalyticsService;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/dashboard/analytics")
public class DashboardAnalyticsController {

    @Autowired
    private DashboardAnalyticsService analyticsService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/overtimes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getOvertimes(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null || admin.getCompany() == null) {
            return ResponseEntity.badRequest().body("Admin ohne Firma");
        }
        return ResponseEntity.ok(analyticsService.getCompanyOvertimes(admin.getCompany().getId()));
    }
}
