package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserScheduleRuleDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/schedule-rules")
public class AdminScheduleRuleController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkScheduleService workScheduleService;

    @Autowired
    private VacationRequestRepository vacationRequestRepository;

    @Autowired
    private UserScheduleRuleRepository ruleRepository;

    @Autowired
    private AccessControlService accessControlService;

    /**
     * GET /api/admin/schedule-rules?userId=XY
     * Liefert alle Regeln für einen bestimmten User.
     */
    @GetMapping
    public ResponseEntity<?> getRulesByUser(@RequestParam Long userId, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        accessControlService.requireCanManageUser(admin, userOpt.get());
        var rules = ruleRepository.findByUser(userOpt.get())
                .stream().map(UserScheduleRuleDTO::new).toList();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllRules(Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        var rules = accessControlService.isSuperAdmin(admin)
                ? ruleRepository.findAll()
                : ruleRepository.findByUser_Company_Id(accessControlService.requireCompanyIdForTenantAdmin(admin));
        return ResponseEntity.ok(rules.stream().map(UserScheduleRuleDTO::new).toList());
    }

    @GetMapping("/expected-work-minutes")
    public int getExpectedWorkMinutes(@RequestParam String username, @RequestParam String date, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        accessControlService.requireCanManageUser(admin, user);
        LocalDate parsedDate = LocalDate.parse(date);
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);

        return workScheduleService.computeExpectedWorkMinutes(user, parsedDate, approvedVacations);
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody UserScheduleRuleDTO dto, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        Optional<User> userOpt = userRepository.findById(dto.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        accessControlService.requireCanManageUser(admin, userOpt.get());

        UserScheduleRule rule = new UserScheduleRule();
        rule.setUser(userOpt.get());
        rule.setRuleType(dto.getRuleType());
        rule.setStartDate(dto.getStartDate());
        rule.setRepeatIntervalDays(dto.getRepeatIntervalDays());
        rule.setDayOfWeek(dto.getDayOfWeek());
        rule.setDayMode(dto.getDayMode());

        ruleRepository.save(rule);
        return ResponseEntity.ok(new UserScheduleRuleDTO(rule));
    }

    /**
     * PUT /api/admin/schedule-rules/{id}
     * Update einer Regel.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody UserScheduleRuleDTO dto, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        Optional<UserScheduleRule> ruleOpt = ruleRepository.findById(id);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Rule not found");
        }
        UserScheduleRule rule = ruleOpt.get();
        accessControlService.requireCanManageUser(admin, rule.getUser());
        if (dto.getRuleType() != null) {
            rule.setRuleType(dto.getRuleType());
        }
        if (dto.getStartDate() != null) {
            rule.setStartDate(dto.getStartDate());
        }
        if (dto.getRepeatIntervalDays() != null) {
            rule.setRepeatIntervalDays(dto.getRepeatIntervalDays());
        }
        if (dto.getDayOfWeek() != null) {
            rule.setDayOfWeek(dto.getDayOfWeek());
        }
        if (dto.getDayMode() != null) {
            rule.setDayMode(dto.getDayMode());
        }
        ruleRepository.save(rule);
        return ResponseEntity.ok(new UserScheduleRuleDTO(rule));
    }

    /**
     * DELETE /api/admin/schedule-rules/{id}
     * Löscht eine Regel.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        Optional<UserScheduleRule> rule = ruleRepository.findById(id);
        if (rule.isEmpty()) {
            return ResponseEntity.badRequest().body("Rule not found");
        }
        accessControlService.requireCanManageUser(admin, rule.get().getUser());
        ruleRepository.deleteById(id);
        return ResponseEntity.ok("Rule deleted");
    }
}
