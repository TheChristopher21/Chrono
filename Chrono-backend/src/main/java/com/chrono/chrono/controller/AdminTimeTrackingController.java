// src/main/java/com/chrono/chrono/controller/AdminTimeTrackingController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    // Neuer GET-Endpoint: Liefert alle Time-Tracking-Einträge inkl. Benutzername
    @GetMapping("/all")
    public ResponseEntity<?> getAllTimeTracks() {
        try {
            List<AdminTimeTrackDTO> dtos = timeTrackingService.getAllTimeTracksWithUser();
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT-Endpoint für die Aktualisierung eines Eintrags
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTimeTrack(
            @PathVariable Long id,
            @RequestParam String newStart,
            @RequestParam String newEnd,
            @RequestParam String adminPassword,
            @RequestParam String userPassword) {

        LocalDateTime parsedStart;
        LocalDateTime parsedEnd;
        try {
            parsedStart = LocalDateTime.parse(newStart);
            parsedEnd = LocalDateTime.parse(newEnd);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid date format.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminUsername = auth.getName();

        try {
            AdminTimeTrackDTO updated = timeTrackingService.updateTimeTrackEntry(id, parsedStart, parsedEnd, adminPassword, userPassword, adminUsername);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
