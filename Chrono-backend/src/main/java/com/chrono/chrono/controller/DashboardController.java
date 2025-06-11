package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/user/{username}/week") // Changed to match the frontend call
    public DashboardResponse getUserDashboard(
            @PathVariable String username,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        // The DashboardService method will likely need to be updated to accept startDate and endDate
        // For example: return dashboardService.getUserDashboardForWeek(username, startDate, endDate);
        return dashboardService.getUserDashboard(username); // This line will need to be adapted
    }
}