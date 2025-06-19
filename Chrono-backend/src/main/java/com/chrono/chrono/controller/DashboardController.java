// src/main/java/com/chrono/controller/DashboardController.java

package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate; // Importieren

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/user/{username}/week")
    public DashboardResponse getUserDashboard(
            @PathVariable String username,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        // Konvertiere die Datums-Strings in LocalDate-Objekte
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        // Rufe die NEUE Service-Methode mit allen Parametern auf
        return dashboardService.getUserDashboardForWeek(username, start, end);
    }
}