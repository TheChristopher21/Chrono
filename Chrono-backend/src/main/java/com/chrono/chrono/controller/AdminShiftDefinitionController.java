package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.ShiftDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/shift-definitions")
public class AdminShiftDefinitionController {

    private final ShiftDefinitionService definitions;
    private final AccessControlService accessControl;

    public AdminShiftDefinitionController(ShiftDefinitionService definitions, AccessControlService accessControl) {
        this.definitions = definitions;
        this.accessControl = accessControl;
    }

    private Company company(Principal principal) {
        return definitions.requireActorCompany(accessControl.requireAuthenticatedUser(principal));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleRule> getActiveRules(Principal principal) {
        return definitions.effectiveRules(company(principal), true);
    }

    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleRule> getAllRules(Principal principal) {
        return definitions.effectiveRules(company(principal), false);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ScheduleRule> saveRule(@RequestBody ScheduleRule rule, Principal principal) {
        return ResponseEntity.ok(definitions.save(company(principal), rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id, Principal principal) {
        definitions.delete(company(principal), id);
        return ResponseEntity.noContent().build();
    }
}
