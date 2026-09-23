package com.chrono.chrono.dto;

public class ChatActionSuggestion {
    private String type;
    private String label;
    private String url;
    private boolean requiresConfirmation;

    public ChatActionSuggestion() {
    }

    public ChatActionSuggestion(String type, String label, String url, boolean requiresConfirmation) {
        this.type = type;
        this.label = label;
        this.url = url;
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }
}
