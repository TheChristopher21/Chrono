package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ProjectService;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private UserService userService;
    @Autowired
    private CustomerService customerService;

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
        project.setCustomer(customer);
        return ResponseEntity.ok(projectService.save(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable Long id, @RequestBody Project projectDetails, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        return projectService.findById(id)
                .map(existing -> {
                    if (existing.getCustomer() == null || !existing.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
                        return ResponseEntity.<Project>status(403).build();
                    }
                    Customer customer = null;
                    if (projectDetails.getCustomer() != null && projectDetails.getCustomer().getId() != null) {
                        customer = customerService.findById(projectDetails.getCustomer().getId()).orElse(null);
                        if (customer == null || !customer.getCompany().getId().equals(user.getCompany().getId())) {
                            return ResponseEntity.<Project>status(403).build();
                        }
                    }
                    existing.setName(projectDetails.getName());
                    existing.setCustomer(customer);
                    return ResponseEntity.ok(projectService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
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
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
