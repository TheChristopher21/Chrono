package com.chrono.chrono.dto;

public class TimeTrackingImportRowDTO {
    private String username;
    private String timestamp;
    private String punchType;
    private String source;
    private String note;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    public String getPunchType() {
        return punchType;
    }
    public void setPunchType(String punchType) {
        this.punchType = punchType;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
}
