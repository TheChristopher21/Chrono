package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ApplicationData;
import com.chrono.chrono.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apply")
public class ApplicationController {

    @Autowired
    private EmailService emailService;

    @PostMapping
    public ResponseEntity<?> apply(@RequestBody ApplicationData data) {

        // Hier nur 1 Übergabe an EmailService:
        emailService.sendRegistrationMail(data);

        return ResponseEntity.ok().body("{\"success\": true}");
    }
}