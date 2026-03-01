package com.chrono.chrono.controller;

import com.chrono.chrono.dto.IntegrationConfigDTO;
import com.chrono.chrono.dto.IntegrationTriggerResultDTO;
import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.entities.IntegrationConfig;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.IntegrationConfigService;
import com.chrono.chrono.services.ReportService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    @Autowired
    private IntegrationConfigService integrationConfigService;

    @Autowired
    private UserService userService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ComplianceAuditService complianceAuditService;

    private boolean featureEnabled(User user) {
        return user != null && user.getCompany() != null && Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled());
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName()) || "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }

    @GetMapping
    public ResponseEntity<List<IntegrationConfigDTO>> listIntegrations(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        List<IntegrationConfig> configs = integrationConfigService.findByCompanyId(user.getCompany().getId());
        List<IntegrationConfigDTO> dtos = new ArrayList<>();
        for (IntegrationConfig config : configs) {
            dtos.add(new IntegrationConfigDTO(config));
        }
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<IntegrationConfigDTO> createIntegration(@RequestBody IntegrationConfigDTO request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        if (request.getName() == null || request.getType() == null) {
            return ResponseEntity.badRequest().build();
        }
        IntegrationConfig config = new IntegrationConfig();
        config.setCompany(user.getCompany());
        config.setName(request.getName());
        config.setType(request.getType());
        config.setEndpointUrl(request.getEndpointUrl());
        config.setAuthHeader(request.getAuthHeader());
        config.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);
        config.setAutoSync(request.getAutoSync() != null ? request.getAutoSync() : Boolean.FALSE);
        IntegrationConfig saved = integrationConfigService.save(config);
        complianceAuditService.recordAction(user, "CREATE", "INTEGRATION", saved.getId(),
                "Integration angelegt: " + saved.getName());
        return ResponseEntity.ok(new IntegrationConfigDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntegrationConfigDTO> updateIntegration(@PathVariable Long id,
                                                                  @RequestBody IntegrationConfigDTO request,
                                                                  Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        Optional<IntegrationConfig> maybeConfig = integrationConfigService.findById(id);
        if (maybeConfig.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        IntegrationConfig config = maybeConfig.get();
        if (!config.getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getType() != null) {
            config.setType(request.getType());
        }
        config.setEndpointUrl(request.getEndpointUrl());
        config.setAuthHeader(request.getAuthHeader());
        if (request.getActive() != null) {
            config.setActive(request.getActive());
        }
        if (request.getAutoSync() != null) {
            config.setAutoSync(request.getAutoSync());
        }
        IntegrationConfig saved = integrationConfigService.save(config);
        complianceAuditService.recordAction(user, "UPDATE", "INTEGRATION", saved.getId(),
                "Integration aktualisiert: " + saved.getName());
        return ResponseEntity.ok(new IntegrationConfigDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        Optional<IntegrationConfig> maybeConfig = integrationConfigService.findById(id);
        if (maybeConfig.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        IntegrationConfig config = maybeConfig.get();
        if (!config.getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        integrationConfigService.delete(id);
        complianceAuditService.recordAction(user, "DELETE", "INTEGRATION", id,
                "Integration entfernt: " + config.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<IntegrationTriggerResultDTO> triggerIntegration(@PathVariable Long id,
                                                                          Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        Optional<IntegrationConfig> maybeConfig = integrationConfigService.findById(id);
        if (maybeConfig.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        IntegrationConfig config = maybeConfig.get();
        if (!config.getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }

        List<ProjectHierarchyNodeDTO> analytics = reportService.getProjectAnalytics(
                user.getCompany().getId(),
                LocalDate.now().minusMonths(1),
                LocalDate.now()
        );

        int recordCount = countNodes(analytics);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("integrationId", config.getId());
        payload.put("generatedAt", LocalDateTime.now());
        payload.put("records", analytics);

        config.setLastTriggeredAt(LocalDateTime.now());
        config.setLastStatus("Bereitgestellt " + recordCount + " Datensätze");
        integrationConfigService.save(config);

        IntegrationTriggerResultDTO result = new IntegrationTriggerResultDTO();
        result.setConfigId(config.getId());
        result.setTriggeredAt(config.getLastTriggeredAt());
        result.setStatus(config.getLastStatus());
        result.setRecordsSent(recordCount);
        result.setPayloadPreview(payload);

        complianceAuditService.recordAction(user, "TRIGGER", "INTEGRATION", config.getId(),
                "Integration ausgelöst: " + config.getName());

        return ResponseEntity.ok(result);
    }

    private int countNodes(List<ProjectHierarchyNodeDTO> nodes) {
        int count = 0;
        for (ProjectHierarchyNodeDTO node : nodes) {
            count += 1;
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                count += countNodes(node.getChildren());
            }
        }
        return count;
    }
}
