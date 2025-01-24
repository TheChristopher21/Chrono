package com.chrono.chrono.controller;

import com.chrono.chrono.entities.TimeTracking;
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
        return timeTrackingService.getLatestTimeTracking(userId);
    }

    @GetMapping("/week/{userId}")
    public List<TimeTracking> getWeekStats(@PathVariable Long userId) {
        return timeTrackingService.getWeekStats(userId);
    }

    @PostMapping("/checkin/{userId}")
    public String checkIn(@PathVariable Long userId) {
        return timeTrackingService.checkIn(userId);
    }

    @PostMapping("/checkout/{userId}")
    public String checkOut(@PathVariable Long userId) {
        return timeTrackingService.checkOut(userId);
    }
}
