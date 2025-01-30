package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/all")
    public List<AdminTimeTrackDTO> getAllTimeTracks() {
        return timeTrackingService.getAllTimeTracksWithUser();
    }
}
