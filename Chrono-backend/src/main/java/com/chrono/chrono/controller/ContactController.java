package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ContactMessage;
import com.chrono.chrono.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Autowired
    private EmailService emailService;

    @PostMapping
    public ResponseEntity<?> sendContact(@RequestBody ContactMessage message) {
        emailService.sendContactMail(message);
        return ResponseEntity.ok().body("{\"success\":true}");
    }
}
