package com.chrono.chrono.dto;

public class ChatStatusResponse {
    private boolean enabled;
    private String model;
    private String provider;
    private int rateLimitMaxRequests;
    private long rateLimitWindowSeconds;
    private String retrievalMode;
    private String agentMode;
    private String message;

    public ChatStatusResponse() {
    }

    public ChatStatusResponse(boolean enabled, String model, String provider, int rateLimitMaxRequests, long rateLimitWindowSeconds, String retrievalMode, String agentMode, String message) {
        this.enabled = enabled;
        this.model = model;
        this.provider = provider;
        this.rateLimitMaxRequests = rateLimitMaxRequests;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.retrievalMode = retrievalMode;
        this.agentMode = agentMode;
        this.message = message;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public String getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(String agentMode) {
        this.agentMode = agentMode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
