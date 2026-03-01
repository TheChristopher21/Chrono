package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.ProjectService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private UserService userService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private ComplianceAuditService complianceAuditService;

    private boolean featureEnabled(Principal principal) {
        if (principal == null) return false;
        User u = userService.getUserByUsername(principal.getName());
        return u.getCompany() != null && Boolean.TRUE.equals(u.getCompany().getCustomerTrackingEnabled());
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAll(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User u = userService.getUserByUsername(principal.getName());
        return ResponseEntity.ok(projectService.findAllByCompanyId(u.getCompany().getId()));
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<List<ProjectHierarchyNodeDTO>> getHierarchy(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        Long companyId = user.getCompany().getId();
        List<Project> projects = projectService.findAllByCompanyIdOrdered(companyId);
        Map<Long, ProjectHierarchyNodeDTO> dtoMap = new LinkedHashMap<>();
        List<ProjectHierarchyNodeDTO> roots = new ArrayList<>();

        for (Project project : projects) {
            String customerName = project.getCustomer() != null ? project.getCustomer().getName() : null;
            Long parentId = project.getParent() != null ? project.getParent().getId() : null;
            ProjectHierarchyNodeDTO node = new ProjectHierarchyNodeDTO(
                    project.getId(),
                    project.getName(),
                    customerName,
                    parentId,
                    project.getBudgetMinutes(),
                    project.getHourlyRate()
            );
            dtoMap.put(project.getId(), node);
        }

        for (Project project : projects) {
            ProjectHierarchyNodeDTO node = dtoMap.get(project.getId());
            Project parent = project.getParent();
            if (parent != null && dtoMap.containsKey(parent.getId())) {
                dtoMap.get(parent.getId()).addChild(node);
            } else {
                roots.add(node);
            }
        }

        return ResponseEntity.ok(roots);
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Project project, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        if (project.getCustomer() == null || project.getCustomer().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Customer customer = customerService.findById(project.getCustomer().getId()).orElse(null);
        if (customer == null || !customer.getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        Project parent = null;
        if (project.getParent() != null && project.getParent().getId() != null) {
            parent = projectService.findById(project.getParent().getId()).orElse(null);
            if (parent == null || parent.getCustomer() == null ||
                    !parent.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
                return ResponseEntity.status(403).build();
            }
        }
        project.setCustomer(customer);
        project.setParent(parent);
        Project saved = projectService.save(project);
        complianceAuditService.recordAction(user, "CREATE", "PROJECT", saved.getId(),
                "Projekt angelegt: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable Long id, @RequestBody Project projectDetails, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();

        User user = userService.getUserByUsername(principal.getName());
        Optional<Project> maybeProject = projectService.findById(id);
        if (maybeProject.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project existing = maybeProject.get();

        if (existing.getCustomer() == null || !existing.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }

        Customer customer = null;
        if (projectDetails.getCustomer() != null && projectDetails.getCustomer().getId() != null) {
            customer = customerService.findById(projectDetails.getCustomer().getId()).orElse(null);
            if (customer == null || !customer.getCompany().getId().equals(user.getCompany().getId())) {
                return ResponseEntity.status(403).build();
            }
        }

        Project parent = null;
        if (projectDetails.getParent() != null && projectDetails.getParent().getId() != null) {
            parent = projectService.findById(projectDetails.getParent().getId()).orElse(null);
            if (parent == null || parent.getCustomer() == null ||
                    !parent.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
                return ResponseEntity.status(403).build();
            }
            if (parent.getId().equals(existing.getId())) {
                return ResponseEntity.badRequest().build();
            }
            java.util.Set<Long> descendantIds = projectService.collectProjectAndDescendantIds(existing);
            if (descendantIds.contains(parent.getId())) {
                return ResponseEntity.status(409).build();
            }
        }

        existing.setName(projectDetails.getName());
        if (customer != null || projectDetails.getCustomer() != null) {
            existing.setCustomer(customer);
        }
        existing.setBudgetMinutes(projectDetails.getBudgetMinutes());
        existing.setHourlyRate(projectDetails.getHourlyRate());
        existing.setParent(parent);
        Project saved = projectService.save(existing);
        complianceAuditService.recordAction(user, "UPDATE", "PROJECT", saved.getId(),
                "Projekt aktualisiert: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        return projectService.findById(id)
                .map(existing -> {
                    if (existing.getCustomer() == null || !existing.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
                        return ResponseEntity.status(403).<Void>build();
                    }
                    projectService.deleteById(id);
                    complianceAuditService.recordAction(user, "DELETE", "PROJECT", id,
                            "Projekt gelöscht: " + existing.getName());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
