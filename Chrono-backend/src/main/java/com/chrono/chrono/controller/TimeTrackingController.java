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


    @PostMapping("/work-start")
    public TimeTrackingResponse workStart(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "WORK_START");
    }

    @PostMapping("/break-start")
    public TimeTrackingResponse breakStart(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "BREAK_START");
    }

    @PostMapping("/break-end")
    public TimeTrackingResponse breakEnd(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "BREAK_END");
    }

    @PostMapping("/work-end")
    public TimeTrackingResponse workEnd(@RequestParam String username) {
        return timeTrackingService.handlePunch(username, "WORK_END");
    }


    @GetMapping("/history")
    public List<TimeTrackingResponse> getUserHistory(@RequestParam String username) {
        return timeTrackingService.getUserHistory(username);
    }
}
