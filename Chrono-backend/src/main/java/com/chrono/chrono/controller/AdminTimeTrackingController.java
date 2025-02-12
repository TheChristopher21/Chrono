package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllTimeTracks() {
        try {
            return ResponseEntity.ok(timeTrackingService.getAllTimeTracksWithUser());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/editDay")
    public ResponseEntity<?> editDayTimeEntries(
            @RequestParam String targetUsername,
            @RequestParam String date,
            @RequestParam String workStart,
            @RequestParam String breakStart,
            @RequestParam String breakEnd,
            @RequestParam String workEnd,
            @RequestParam String adminUsername,
            @RequestParam String adminPassword,
            @RequestParam String userPassword) {

        try {
            String result = timeTrackingService.updateDayTimeEntries(
                    targetUsername,
                    date,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    adminUsername,
                    adminPassword,
                    userPassword);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
