package com.chrono.chrono.controller;

import com.chrono.chrono.services.SlackService;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatbotTimeTrackingController {

    @Autowired
    private TimeTrackingService timeTrackingService;
    @Autowired
    private SlackService slackService;

    @PostMapping("/stamp")
    public ResponseEntity<?> stamp(@RequestParam String username, @RequestParam String type) {
        try {
            timeTrackingService.handlePunch(username, com.chrono.chrono.entities.TimeTrackingEntry.PunchSource.CHATBOT);
            slackService.sendMessage("User " + username + " hat gestempelt: " + type);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
