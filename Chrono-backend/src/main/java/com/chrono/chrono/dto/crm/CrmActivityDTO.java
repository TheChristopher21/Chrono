package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmActivity;
import com.chrono.chrono.entities.crm.CrmActivityType;

import java.time.LocalDateTime;

public class CrmActivityDTO {
    private final Long id;
    private final CrmActivityType type;
    private final String notes;
    private final String owner;
    private final LocalDateTime createdAt;
    private final Long contactId;

    public CrmActivityDTO(Long id, CrmActivityType type, String notes, String owner,
                          LocalDateTime createdAt, Long contactId) {
        this.id = id;
        this.type = type;
        this.notes = notes;
        this.owner = owner;
        this.createdAt = createdAt;
        this.contactId = contactId;
    }

    public static CrmActivityDTO from(CrmActivity activity) {
        return new CrmActivityDTO(
                activity.getId(),
                activity.getType(),
                activity.getNotes(),
                activity.getOwner(),
                activity.getTimestamp(),
                activity.getContact() != null ? activity.getContact().getId() : null);
    }

    public Long getId() {
        return id;
    }

    public CrmActivityType getType() {
        return type;
    }

    public String getNotes() {
        return notes;
    }

    public String getOwner() {
        return owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getContactId() {
        return contactId;
    }
}
