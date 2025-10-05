package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmActivity;
import com.chrono.chrono.entities.crm.CrmActivityType;

public class CreateCrmActivityRequest {
    private CrmActivityType type = CrmActivityType.NOTE;
    private String notes;
    private Long contactId;
    private java.time.LocalDateTime timestamp;

    public CrmActivityType getType() {
        return type;
    }

    public void setType(CrmActivityType type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public java.time.LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(java.time.LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public CrmActivity toEntity() {
        CrmActivity activity = new CrmActivity();
        activity.setType(type);
        activity.setNotes(notes);
        if (timestamp != null) {
            activity.setTimestamp(timestamp);
        }
        return activity;
    }
}
