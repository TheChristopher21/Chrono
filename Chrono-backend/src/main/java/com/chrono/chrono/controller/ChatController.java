package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChatRequest;
import com.chrono.chrono.dto.ChatResponse;
import com.chrono.chrono.services.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        String answer = chatService.ask(req.getMessage());
        return ResponseEntity.ok(new ChatResponse(answer));
    }
}
