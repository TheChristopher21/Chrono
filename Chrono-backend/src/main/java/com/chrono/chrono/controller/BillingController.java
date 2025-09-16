package com.chrono.chrono.controller;

import com.chrono.chrono.dto.GenerateInvoiceRequestDTO;
import com.chrono.chrono.dto.InvoiceSummaryDTO;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.BillingService;
import com.chrono.chrono.services.ProjectService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    private boolean featureEnabled(User user) {
        return user != null && user.getCompany() != null && Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled());
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName()) || "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }

    @PostMapping("/invoice")
    public ResponseEntity<InvoiceSummaryDTO> generateInvoice(@RequestBody GenerateInvoiceRequestDTO request,
                                                             Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user) || !isAdmin(user)) {
            return ResponseEntity.status(403).build();
        }
        if (request.getProjectId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Project> maybeProject = projectService.findById(request.getProjectId());
        if (maybeProject.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Project project = maybeProject.get();
        if (project.getCustomer() == null || project.getCustomer().getCompany() == null ||
                !project.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        LocalDate start = LocalDate.parse(request.getStartDate());
        LocalDate end = LocalDate.parse(request.getEndDate());
        InvoiceSummaryDTO summary = billingService.generateInvoice(
                user,
                project,
                start,
                end,
                request.isIncludeChildren(),
                request.getOverrideRate(),
                request.getCurrency()
        );
        return ResponseEntity.ok(summary);
    }
}
