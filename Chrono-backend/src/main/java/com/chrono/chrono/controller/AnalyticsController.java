package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AnalyticsExcludedIpRequest;
import com.chrono.chrono.dto.AnalyticsExcludedIpResponse;
import com.chrono.chrono.dto.AnalyticsEventRequest;
import com.chrono.chrono.dto.AnalyticsSummaryResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    @PostMapping("/public/analytics/events")
    public ResponseEntity<Void> trackEvent(
            @RequestBody(required = false) AnalyticsEventRequest request,
            HttpServletRequest servletRequest,
            Principal principal
    ) {
        if (principal != null && isSuperAdmin(principal.getName())) {
            return ResponseEntity.accepted().build();
        }

        analyticsService.recordEvent(request, servletRequest);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/superadmin/analytics/summary")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public AnalyticsSummaryResponse getSummary(@RequestParam(defaultValue = "14") int days) {
        return analyticsService.getSummary(days);
    }

    @GetMapping("/superadmin/analytics/excluded-ips")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<AnalyticsExcludedIpResponse> getExcludedIps() {
        return analyticsService.getExcludedIps();
    }

    @PostMapping("/superadmin/analytics/excluded-ips")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public AnalyticsExcludedIpResponse addExcludedIp(@RequestBody AnalyticsExcludedIpRequest request) {
        return analyticsService.addExcludedIp(request);
    }

    @DeleteMapping("/superadmin/analytics/excluded-ips/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> removeExcludedIp(@PathVariable Long id) {
        analyticsService.removeExcludedIp(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isSuperAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(this::isSuperAdmin)
                .orElse(false);
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }
}
