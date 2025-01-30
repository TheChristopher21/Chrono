package com.chrono.chrono.controller;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.dto.TimeTrackingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    @Autowired
    private UserService userService;
    @Autowired
    private TimeTrackingService timeTrackingService;

    // 1) Team-Users
    @GetMapping("/team/users")
    public List<User> getTeamUsers(Principal principal) {
        // manager's username
        String managerName = principal.getName();
        User manager = userService.getUserByUsername(managerName);
        return userService.getUsersByManager(manager);
    }

    // 2) Team-Time
    @GetMapping("/team/time")
    public List<TimeTrackingResponse> getTeamTime(Principal principal) {
        User manager = userService.getUserByUsername(principal.getName());
        return timeTrackingService.getTeamTimeTracks(manager);
    }

    // 3) Team Corrections – du musst definieren, wie du in CorrectionService
    //    z.B. alle CorrectionRequests des Teams findest
    @GetMapping("/team/corrections")
    public List<?> getTeamCorrections(Principal principal) {
        // ...
        return List.of(); // implement in CorrectionRequestService
    }
}
