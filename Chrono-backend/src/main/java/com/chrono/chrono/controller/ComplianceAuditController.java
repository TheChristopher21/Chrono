package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ComplianceAuditLogDTO;
import com.chrono.chrono.entities.ComplianceAuditLog;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
public class ComplianceAuditController {

    @Autowired
    private ComplianceAuditService complianceAuditService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<ComplianceAuditLogDTO>> getRecent(@RequestParam(name = "limit", defaultValue = "25") int limit,
                                                                 Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (user.getCompany() == null) {
            return ResponseEntity.status(403).build();
        }
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName()) || "ROLE_SUPERADMIN".equals(role.getRoleName()));
        if (!isAdmin) {
            return ResponseEntity.status(403).build();
        }
        List<ComplianceAuditLog> logs = complianceAuditService.getRecentLogs(user.getCompany().getId(), limit);
        List<ComplianceAuditLogDTO> payload = logs.stream()
                .map(ComplianceAuditLogDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(payload);
    }
}
