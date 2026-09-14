// src/main/java/com/chrono/controller/DashboardController.java

package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.DashboardService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate; // Importieren

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AccessControlService accessControlService;

    public DashboardController(DashboardService dashboardService, AccessControlService accessControlService) {
        this.dashboardService = dashboardService;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/user/{username}/week")
    public DashboardResponse getUserDashboard(
            @PathVariable String username,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Principal principal) {

        User actor = accessControlService.requireAuthenticatedUser(principal);
        User target = actor.getUsername().equals(username)
                ? actor
                : accessControlService.requireTargetUser(username);
        accessControlService.requireCanAccessUser(actor, target);

        // Konvertiere die Datums-Strings in LocalDate-Objekte
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        // Rufe die NEUE Service-Methode mit allen Parametern auf
        return dashboardService.getUserDashboardForWeek(username, start, end);
    }
}
