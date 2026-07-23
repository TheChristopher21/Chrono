package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.NfcAgentAuthService;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
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
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private NfcAgentAuthService nfcAgentAuthService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;


    @PostMapping("/punch")
    public ResponseEntity<TimeTrackingEntryDTO> punch(
        @RequestParam String username,
        @RequestParam(value = "customerId", required = false) Long customerId,
        @RequestParam(value = "projectId", required = false) Long projectId,
        @RequestParam(value = "taskId", required = false) Long taskId,
        @RequestParam(value = "durationMinutes", required = false) Integer durationMinutes,
        @RequestParam(value = "description", required = false) String description,
        Principal principal,
        @RequestHeader(value = "X-Agent-Token", required = false) String nfcAgentToken,
        @RequestHeader(value = "X-NFC-Agent-Request", required = false) String nfcAgentHeader,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "source", required = false) String sourceStr,
        HttpServletRequest httpRequest
    ) {
        User requestingUser = resolveRequestingUser(principal, authorizationHeader);
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
        

        boolean isAllowed = requestingUser != null
                ? accessControlService.canAccessUser(requestingUser, targetUser)
                : punchSource == TimeTrackingEntry.PunchSource.NFC_SCAN
                    && nfcAgentAuthService.isAgentRequest(nfcAgentToken, nfcAgentHeader, httpRequest);

        if (!isAllowed) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        try {
            TimeTrackingEntryDTO newEntry = timeTrackingService.handlePunch(username, punchSource, customerId, projectId, taskId, durationMinutes, description);
            return ResponseEntity.ok(newEntry);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private User resolveRequestingUser(Principal principal, String authorizationHeader) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return userService.getUserByUsername(principal.getName());
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7);
        try {
            String username = jwtUtil.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (Boolean.TRUE.equals(jwtUtil.validateToken(token, userDetails))) {
                return userService.getUserByUsername(username);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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

    @GetMapping("/period-summary")
    public ResponseEntity<TimePeriodSummaryDTO> getPeriodSummary(
            @RequestParam String username,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal
    ) {
        User requestingUser = userService.getUserByUsername(principal.getName());
        User targetUser = userService.getUserByUsername(username);
        boolean canAccess = requestingUser.getId().equals(targetUser.getId()) ||
                requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN")) ||
                (requestingUser.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN")) &&
                        requestingUser.getCompany() != null && targetUser.getCompany() != null &&
                        requestingUser.getCompany().getId().equals(targetUser.getCompany().getId()));
        if (!canAccess) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(timeTrackingService.getUserPeriodSummary(targetUser, startDate, endDate));
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
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        User targetUser = accessControlService.requireTargetUser(username);
        accessControlService.requireCanAccessUser(requestingUser, targetUser);
        try {
            timeTrackingService.assignCustomerForDay(username, LocalDate.parse(date), customerId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/entry/{id}/approve")
    public ResponseEntity<TimeTrackingEntryDTO> approveEntry(@PathVariable Long id, Principal principal) {
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        boolean allowed = accessControlService.isAdmin(requestingUser);
        if (!allowed) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(timeTrackingService.approveEntry(id, requestingUser.getUsername()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/entry/{id}/revoke-approval")
    public ResponseEntity<TimeTrackingEntryDTO> revokeApproval(@PathVariable Long id, Principal principal) {
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        boolean allowed = accessControlService.isAdmin(requestingUser);
        if (!allowed) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(timeTrackingService.revokeApproval(id, requestingUser.getUsername()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PutMapping("/range/customer")
    public ResponseEntity<Void> assignCustomerForRange(
            @RequestParam String username,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) Long customerId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        User targetUser = accessControlService.requireTargetUser(username);
        accessControlService.requireCanAccessUser(requestingUser, targetUser);
        try {
            timeTrackingService.assignCustomerForTimeRange(
                    username,
                    LocalDate.parse(date),
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    customerId);
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
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        User targetUser = accessControlService.requireTargetUser(username);
        accessControlService.requireCanAccessUser(requestingUser, targetUser);
        try {
            timeTrackingService.assignProjectForDay(username, LocalDate.parse(date), projectId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/daily-note")
    public ResponseEntity<Void> saveDailyNote(
            @RequestParam String username,
            @RequestParam String date,
            @RequestBody Map<String, String> body,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User requestingUser = accessControlService.requireAuthenticatedUser(principal);
        User targetUser = accessControlService.requireTargetUser(username);
        accessControlService.requireCanAccessUser(requestingUser, targetUser);
        String note = body.get("note");
        if (note == null) note = "";
        try {
            timeTrackingService.saveDailyNote(username, LocalDate.parse(date), note);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
