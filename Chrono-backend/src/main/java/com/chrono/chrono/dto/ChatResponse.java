package com.chrono.chrono.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {
    private String answer;
    private String model;
    private Long latencyMs;
    private List<String> sources = new ArrayList<>();
    private List<ChatActionSuggestion> suggestions = new ArrayList<>();
    private String status;
    private String safetyLevel;
    private String retrievalMode;
    private Integer remainingRequests;
    private Long retryAfterSeconds;

    public ChatResponse() {}

    public ChatResponse(String answer) {
        this.answer = answer;
    }

    public ChatResponse(ChatResult result) {
        if (result == null) {
            return;
        }
        this.answer = result.getAnswer();
        this.model = result.getModel();
        this.latencyMs = result.getLatencyMs();
        this.sources = result.getSources();
        this.suggestions = result.getSuggestions();
        this.status = result.getStatus();
        this.safetyLevel = result.getSafetyLevel();
        this.retrievalMode = result.getRetrievalMode();
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
    }

    public List<ChatActionSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ChatActionSuggestion> suggestions) {
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSafetyLevel() {
        return safetyLevel;
    }

    public void setSafetyLevel(String safetyLevel) {
        this.safetyLevel = safetyLevel;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public Integer getRemainingRequests() {
        return remainingRequests;
    }

    public void setRemainingRequests(Integer remainingRequests) {
        this.remainingRequests = remainingRequests;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
