package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.entities.ScheduleRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/shift-definitions")
public class AdminShiftDefinitionController {

    @Autowired
    private ScheduleRuleRepository ruleRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<ScheduleRule> getActiveRules() {
        return ruleRepository.findByIsActiveTrueOrderByStartTime();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<ScheduleRule> getAllRules() {
        return ruleRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ScheduleRule> saveRule(@RequestBody ScheduleRule rule) {
        if (rule.getId() != null) {
            Optional<ScheduleRule> existingRuleOpt = ruleRepository.findById(rule.getId());
            if (existingRuleOpt.isPresent()) {
                ScheduleRule existingRule = existingRuleOpt.get();
                existingRule.setLabel(rule.getLabel());
                existingRule.setStartTime(rule.getStartTime());
                existingRule.setEndTime(rule.getEndTime());
                existingRule.setActive(rule.getIsActive());

                ScheduleRule updatedRule = ruleRepository.save(existingRule);
                return ResponseEntity.ok(updatedRule);
            }
        }

        ScheduleRule newRule = ruleRepository.save(rule);
        return ResponseEntity.ok(newRule);
    }
}