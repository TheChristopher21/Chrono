package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ProjectService;
import com.chrono.chrono.services.TaskService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

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
    public ResponseEntity<List<Task>> getTasks(@RequestParam(value = "projectId", required = false) Long projectId,
                                               Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        if (projectId != null) {
            Optional<Project> project = projectService.findById(projectId);
            if (project.isEmpty() || project.get().getCustomer() == null ||
                !project.get().getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(taskService.getTasks(projectId));
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        if (task.getProject() == null || task.getProject().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Project project = projectService.findById(task.getProject().getId()).orElse(null);
        if (project == null || project.getCustomer() == null ||
            !project.getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        task.setProject(project);
        return ResponseEntity.ok(taskService.save(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task taskDetails, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        Optional<Task> existingOpt = taskService.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Task existing = existingOpt.get();
        if (existing.getProject() == null || existing.getProject().getCustomer() == null ||
            !existing.getProject().getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        existing.setName(taskDetails.getName());
        existing.setBudgetMinutes(taskDetails.getBudgetMinutes());
        existing.setBillable(taskDetails.getBillable());
        return ResponseEntity.ok(taskService.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        User user = userService.getUserByUsername(principal.getName());
        Optional<Task> existingOpt = taskService.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Task existing = existingOpt.get();
        if (existing.getProject() == null || existing.getProject().getCustomer() == null ||
            !existing.getProject().getCustomer().getCompany().getId().equals(user.getCompany().getId())) {
            return ResponseEntity.status(403).build();
        }
        taskService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
