package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserScheduleRuleDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /**
     * GET /api/admin/schedule-rules?userId=XY
     * Liefert alle Regeln für einen bestimmten User.
     */
    @GetMapping
    public ResponseEntity<?> getRulesByUser(@RequestParam Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        var rules = ruleRepository.findByUser(userOpt.get())
                .stream().map(UserScheduleRuleDTO::new).toList();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/expected-work-minutes")
    public int getExpectedWorkMinutes(@RequestParam String username, @RequestParam String date) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        LocalDate parsedDate = LocalDate.parse(date);

        // Genehmigte Urlaube für diesen User laden
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(user);

        // Die korrigierte Methode im WorkScheduleService aufrufen
        return workScheduleService.computeExpectedWorkMinutes(user, parsedDate, approvedVacations);
    }
    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody UserScheduleRuleDTO dto) {
        Optional<User> userOpt = userRepository.findById(dto.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

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
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody UserScheduleRuleDTO dto) {
        Optional<UserScheduleRule> ruleOpt = ruleRepository.findById(id);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Rule not found");
        }
        UserScheduleRule rule = ruleOpt.get();
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
    public ResponseEntity<?> deleteRule(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("Rule not found");
        }
        ruleRepository.deleteById(id);
        return ResponseEntity.ok("Rule deleted");
    }
}
