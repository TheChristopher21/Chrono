package com.chrono.chrono.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    private String message;
    private List<ChatMessage> history = new ArrayList<>();

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history != null ? history : new ArrayList<>();
    }

    public static class ChatMessage {
        private String sender;
        private String text;

        public ChatMessage() {}

        public ChatMessage(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
