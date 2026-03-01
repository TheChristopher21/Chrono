package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChatRequest;
import com.chrono.chrono.dto.ChatResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ChatService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req, Principal principal) {
        User user = null;
        if (principal != null) {
            user = userService.getUserByUsername(principal.getName());
        }
        String answer = chatService.ask(req.getMessage(), user);
        return ResponseEntity.ok(new ChatResponse(answer));
    }
}
