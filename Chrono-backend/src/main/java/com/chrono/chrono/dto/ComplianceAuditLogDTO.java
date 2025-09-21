package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ComplianceAuditLog;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
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

}
