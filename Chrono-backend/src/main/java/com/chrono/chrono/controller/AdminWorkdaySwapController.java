package com.chrono.chrono.controller;

import com.chrono.chrono.dto.WorkdaySwapDTO;
import com.chrono.chrono.dto.WorkdaySwapRequestDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.WorkdaySwapService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/timetracking/workday-swaps")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminWorkdaySwapController {
    private final WorkdaySwapService workdaySwapService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public AdminWorkdaySwapController(
            WorkdaySwapService workdaySwapService,
            UserRepository userRepository,
            UserPermissionService userPermissionService
    ) {
        this.workdaySwapService = workdaySwapService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal
    ) {
        try {
            User admin = requireAdmin(principal, UserPermissionService.ACCESS_VIEW);
            List<WorkdaySwapDTO> swaps = workdaySwapService.list(admin, username, startDate, endDate);
            return ResponseEntity.ok(swaps);
        } catch (SecurityException | AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WorkdaySwapRequestDTO request, Principal principal) {
        try {
            User admin = requireAdmin(principal, UserPermissionService.ACCESS_MANAGE);
            return ResponseEntity.ok(workdaySwapService.create(admin, request));
        } catch (SecurityException | AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/{swapId}")
    public ResponseEntity<?> delete(@PathVariable Long swapId, Principal principal) {
        try {
            User admin = requireAdmin(principal, UserPermissionService.ACCESS_MANAGE);
            workdaySwapService.delete(admin, swapId);
            return ResponseEntity.ok(Map.of("message", "Arbeitstagtausch wurde entfernt."));
        } catch (SecurityException | AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    private User requireAdmin(Principal principal, String accessLevel) {
        User admin = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new SecurityException("Admin wurde nicht gefunden."));
        userPermissionService.assertPageAccess(
                admin,
                UserPermissionService.PAGE_ADMIN_DASHBOARD,
                accessLevel,
                "Keine Berechtigung für Arbeitstagtausche."
        );
        return admin;
    }
}
