package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ProjectService;
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

    private boolean featureEnabled(Principal principal) {
        if (principal == null) return false;
        User u = userService.getUserByUsername(principal.getName());
        return u.getCompany() != null && Boolean.TRUE.equals(u.getCompany().getCustomerTrackingEnabled());
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAll(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(projectService.findAll());
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Project project, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(projectService.save(project));
    }
}
