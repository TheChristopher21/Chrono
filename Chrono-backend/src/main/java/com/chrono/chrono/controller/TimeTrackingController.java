package com.chrono.chrono.controller;

import com.chrono.chrono.dto.TimeTrackingRequest;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetracking")
public class TimeTrackingController {

    private final TimeTrackingService timeTrackingService;

    public TimeTrackingController(TimeTrackingService timeTrackingService) {
        this.timeTrackingService = timeTrackingService;
    }

    @PostMapping("/punch-in")
    public ResponseEntity<TimeTracking> punchIn(@RequestBody TimeTrackingRequest request) {
        return ResponseEntity.ok(timeTrackingService.punchIn(request));
    }

    @PostMapping("/punch-out")
    public ResponseEntity<TimeTracking> punchOut(@RequestBody TimeTrackingRequest request) {
        return ResponseEntity.ok(timeTrackingService.punchOut(request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<TimeTracking>> getUserTimeTracking(@PathVariable Long userId) {
        return ResponseEntity.ok(timeTrackingService.getUserTimeTracking(userId));
    }
}
