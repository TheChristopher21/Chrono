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

        emailService.sendRegistrationNotification(
                data.getCompanyName(),
                data.getContactName(),
                data.getEmail(),
                data.getPhone(),
                data.getAdditionalInfo(),
                data.getChosenPackage() // <<-------------
        );

        return ResponseEntity.ok().body("{\"success\": true}");
    }
}
