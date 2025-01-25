package com.chrono.chrono.controller;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/current/{userId}")
    public TimeTracking getLatestTimeTracking(@PathVariable Long userId) {
        // User wird anhand der userId simuliert, oder passe den Service an, um direkt mit der ID zu arbeiten
        User user = new User();
        user.setId(userId);
        return timeTrackingService.getLatestTimeTracking(user);
    }

    @GetMapping("/week/{userId}")
    public List<TimeTracking> getWeekStats(@PathVariable Long userId) {
        // Hier wird ebenfalls ein User-Objekt erstellt
        return timeTrackingService.getWeekStats(userId);
    }

    @PostMapping("/checkin/{userId}")
    public String checkIn(@PathVariable Long userId) {
        // Simuliertes User-Objekt
        User user = new User();
        user.setId(userId);
        return timeTrackingService.checkIn(user);
    }

    @PostMapping("/checkout/{userId}")
    public String checkOut(@PathVariable Long userId) {
        // Simuliertes User-Objekt
        User user = new User();
        user.setId(userId);
        return timeTrackingService.checkOut(user);
    }
}
