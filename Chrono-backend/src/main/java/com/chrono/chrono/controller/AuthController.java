package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthRequest;
import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        String token = "dummy-token"; // Hier sollte dein JWT-Token generiert werden.
        return new AuthResponse(user.getUsername(), user.getRole().getName(), user.getId(), token);
    }

}
