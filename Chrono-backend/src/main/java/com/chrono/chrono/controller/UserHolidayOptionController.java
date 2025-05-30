package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserHolidayOptionDTO;
import com.chrono.chrono.entities.UserHolidayOption;
import com.chrono.chrono.services.UserHolidayOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user-holiday-options")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class UserHolidayOptionController {

    @Autowired
    private UserHolidayOptionService userHolidayOptionService;

    @GetMapping
    public ResponseEntity<?> getHolidayOption(
            @RequestParam String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            UserHolidayOptionDTO dto = userHolidayOptionService.getOrCreateHolidayOption(username, date);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error retrieving holiday option: " + e.getMessage()));
        }
    }

    @GetMapping("/week")
    public ResponseEntity<?> getHolidayOptionsForWeek(
            @RequestParam String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mondayInWeek) {
        try {
            List<UserHolidayOptionDTO> dtos = userHolidayOptionService.getHolidayOptionsForUserAndWeek(username, mondayInWeek);
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error retrieving weekly holiday options: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> setHolidayOption(
            @RequestParam String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String option, // PENDING_DECISION, DEDUCT_FROM_WEEKLY_TARGET, DO_NOT_DEDUCT_FROM_WEEKLY_TARGET
            Principal principal) {
        try {
            UserHolidayOption.HolidayHandlingOption optionEnum = UserHolidayOption.HolidayHandlingOption.valueOf(option.toUpperCase());
            UserHolidayOptionDTO dto = userHolidayOptionService.setHolidayOption(username, date, optionEnum, principal.getName());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            // Z.B. wenn der String nicht in das Enum umgewandelt werden kann oder User nicht prozentual etc.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error setting holiday option: " + e.getMessage()));
        }
    }
}