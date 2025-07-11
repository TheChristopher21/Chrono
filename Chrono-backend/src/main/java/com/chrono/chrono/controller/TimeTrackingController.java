package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    @Autowired
    private UserService userService;
    @Autowired
    private TimeTrackingService timeTrackingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VacationRequestRepository vacationRequestRepository;


    @PostMapping("/punch")
    public ResponseEntity<TimeTrackingEntryDTO> punch(
        @RequestParam String username,
        @RequestParam(value = "customerId", required = false) Long customerId,
        @RequestParam(value = "projectId", required = false) Long projectId,
        Principal principal,
        @RequestHeader(value = "X-NFC-Agent-Request", required = false) String nfcAgentHeader,
        @RequestParam(value = "source", required = false) String sourceStr
    ) {
        User requestingUser = (principal != null) ? userService.getUserByUsername(principal.getName()) : null;
        User targetUser = userService.getUserByUsername(username);

        TimeTrackingEntry.PunchSource punchSource;
        if (sourceStr != null && !sourceStr.isBlank()) {
            try {
                punchSource = TimeTrackingEntry.PunchSource.valueOf(sourceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            punchSource = (nfcAgentHeader != null && nfcAgentHeader.equals("true")) ?
                          TimeTrackingEntry.PunchSource.NFC_SCAN :
                          TimeTrackingEntry.PunchSource.MANUAL_PUNCH;
        }
        

        boolean isAllowed = false;
        if (requestingUser != null) {
            if (requestingUser.getId().equals(targetUser.getId())) {
                isAllowed = true;
            } else if (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
                isAllowed = true;
            } else if (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN"))) {
                if (requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                    requestingUser.getCompany().getId().equals(targetUser.getCompany().getId())) {
                    isAllowed = true;
                }
            }
        } else {
            if (punchSource == TimeTrackingEntry.PunchSource.NFC_SCAN && nfcAgentHeader != null && nfcAgentHeader.equals("true")) {
                isAllowed = true;
            } else if (username.equals("nfc-background-service") && punchSource == TimeTrackingEntry.PunchSource.NFC_SCAN) { 
                isAllowed = true;
            }
        }

        if (!isAllowed) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        try {
            TimeTrackingEntryDTO newEntry = timeTrackingService.handlePunch(username, punchSource, customerId, projectId);
            return ResponseEntity.ok(newEntry);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<DailyTimeSummaryDTO>> getUserHistory(@RequestParam String username, Principal principal) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);
        boolean canAccess = requestingUser.getId().equals(targetUser.getId()) ||
                            requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                            (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                             requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                             requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()));
        if (canAccess) {
            return ResponseEntity.ok(timeTrackingService.getUserHistory(username));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<DailyTimeSummaryDTO> getDailySummary(
        @RequestParam String username,
        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
        Principal principal
    ) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);
         boolean canAccess = requestingUser.getId().equals(targetUser.getId()) ||
                            requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                            (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                             requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                             requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()));
        if(canAccess) {
            return ResponseEntity.ok(timeTrackingService.getDailySummary(username, date));
        } else {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/report")
    public ResponseEntity<List<TimeReportDTO>> getReport(
            @RequestParam String username, @RequestParam String startDate, @RequestParam String endDate, Principal principal) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);
        boolean canAccess = requestingUser.getId().equals(targetUser.getId()) ||
                            requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                            (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                             requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                             requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()));
        if (canAccess) {
            return ResponseEntity.ok(timeTrackingService.getReport(username, startDate, endDate));
        } else {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/work-difference")
    public ResponseEntity<Map<String, Integer>> getWorkDifference(
            @RequestParam String username, @RequestParam String date, Principal principal) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        LocalDate parsedDate = LocalDate.parse(date);
        boolean canAccess = requestingUser.getId().equals(targetUser.getId()) ||
                            requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                            (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                             requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                             requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()));
        if (!canAccess) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<TimeTrackingEntry> entriesForDay = timeTrackingService.getTimeTrackingEntriesForUserAndDate(targetUser, parsedDate);
        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(targetUser);
        int difference = timeTrackingService.computeDailyWorkDifference(targetUser, parsedDate, approvedVacations, entriesForDay);
        return ResponseEntity.ok(Map.of("dailyDifferenceMinutes", difference));
    }
}

    @PutMapping("/entry/{id}/customer")
    public ResponseEntity<TimeTrackingEntryDTO> updateEntryCustomer(
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            TimeTrackingEntryDTO dto = timeTrackingService.updateEntryCustomer(id, customerId, principal.getName());
            return ResponseEntity.ok(dto);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/day/customer")
    public ResponseEntity<Void> assignCustomerForDay(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam(required = false) Long customerId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            timeTrackingService.assignCustomerForDay(username, LocalDate.parse(date), customerId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/entry/{id}/project")
    public ResponseEntity<TimeTrackingEntryDTO> updateEntryProject(
            @PathVariable Long id,
            @RequestParam(required = false) Long projectId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            TimeTrackingEntryDTO dto = timeTrackingService.updateEntryProject(id, projectId, principal.getName());
            return ResponseEntity.ok(dto);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/day/project")
    public ResponseEntity<Void> assignProjectForDay(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam(required = false) Long projectId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            timeTrackingService.assignProjectForDay(username, LocalDate.parse(date), projectId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
