package com.chrono.chrono.controller;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time-tracking")
public class TimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/latest/{userId}")
    public ResponseEntity<?> getLatestTimeTracking(@PathVariable Long userId) {
        System.out.println("GET /latest/{userId} aufgerufen mit User-ID: " + userId); // Debugging-Log
        try {
            TimeTracking latestTracking = timeTrackingService.getLatestTimeTracking(userId);
            System.out.println("Erfolgreiche Rückgabe: " + latestTracking); // Debugging-Log
            return ResponseEntity.ok(latestTracking);
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der letzten Zeiterfassung: " + e.getMessage());
            return ResponseEntity.status(500).body("Fehler beim Abrufen der letzten Zeiterfassung.");
        }
    }

    @GetMapping("/week/{userId}")
    public ResponseEntity<?> getWeekStats(@PathVariable Long userId) {
        System.out.println("GET /week/{userId} aufgerufen mit User-ID: " + userId); // Debugging-Log
        try {
            List<TimeTracking> weekStats = timeTrackingService.getWeekStats(userId);
            System.out.println("Erfolgreiche Wochenstatistik-Rückgabe: " + weekStats); // Debugging-Log
            return ResponseEntity.ok(weekStats);
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Wochenstatistik: " + e.getMessage());
            return ResponseEntity.status(500).body("Fehler beim Abrufen der Wochenstatistik.");
        }
    }

    @PostMapping("/check-in/{userId}")
    public ResponseEntity<?> checkIn(@PathVariable Long userId) {
        System.out.println("POST /check-in/{userId} aufgerufen mit User-ID: " + userId); // Debugging-Log
        try {
            String message = timeTrackingService.checkIn(userId);
            System.out.println("Check-In erfolgreich: " + message); // Debugging-Log
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            System.err.println("Fehler beim Check-In: " + e.getMessage());
            return ResponseEntity.status(500).body("Fehler beim Check-In.");
        }
    }

    @PostMapping("/check-out/{userId}")
    public ResponseEntity<?> checkOut(@PathVariable Long userId) {
        System.out.println("POST /check-out/{userId} aufgerufen mit User-ID: " + userId); // Debugging-Log
        try {
            String message = timeTrackingService.checkOut(userId);
            System.out.println("Check-Out erfolgreich: " + message); // Debugging-Log
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            System.err.println("Fehler beim Check-Out: " + e.getMessage());
            return ResponseEntity.status(500).body("Fehler beim Check-Out.");
        }
    }
}
