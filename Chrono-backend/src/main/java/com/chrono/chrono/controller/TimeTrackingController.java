package com.chrono.chrono.controller;

import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @PostMapping("/punch-in")
    public TimeTrackingResponse punchIn(@RequestParam String username) {
        return timeTrackingService.punchIn(username);
    }

    @PostMapping("/punch-out")
    public TimeTrackingResponse punchOut(@RequestParam String username) {
        return timeTrackingService.punchOut(username);
    }

    @GetMapping("/history")
    public List<TimeTrackingResponse> getUserHistory(@RequestParam String username) {
        return timeTrackingService.getUserHistory(username);
    }
}
