package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ComplianceAuditLog;

import java.time.LocalDateTime;

public class ComplianceAuditLogDTO {

    private Long id;
    private String username;
    private String action;
    private String targetType;
    private Long targetId;
    private String details;
    private String severity;
    private LocalDateTime createdAt;

    public static ComplianceAuditLogDTO fromEntity(ComplianceAuditLog log) {
        ComplianceAuditLogDTO dto = new ComplianceAuditLogDTO();
        dto.setId(log.getId());
        dto.setUsername(log.getUsername());
        dto.setAction(log.getAction());
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setDetails(log.getDetails());
        dto.setSeverity(log.getSeverity() != null ? log.getSeverity().name() : null);
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
