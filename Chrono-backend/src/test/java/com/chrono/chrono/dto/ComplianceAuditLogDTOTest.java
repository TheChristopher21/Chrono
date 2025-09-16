package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ComplianceAuditLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComplianceAuditLogDTOTest {

    @Test
    void fromEntityCopiesAllScalarFields() {
        ComplianceAuditLog entity = new ComplianceAuditLog();
        entity.setId(12L);
        entity.setUsername("carla");
        entity.setAction("CREATE");
        entity.setTargetType("PROJECT");
        entity.setTargetId(42L);
        entity.setDetails("Created project");
        entity.setSeverity(ComplianceAuditLog.Severity.WARNING);
        LocalDateTime created = LocalDateTime.now();
        entity.setCreatedAt(created);

        ComplianceAuditLogDTO dto = ComplianceAuditLogDTO.fromEntity(entity);

        assertEquals(12L, dto.getId());
        assertEquals("carla", dto.getUsername());
        assertEquals("CREATE", dto.getAction());
        assertEquals("PROJECT", dto.getTargetType());
        assertEquals(42L, dto.getTargetId());
        assertEquals("Created project", dto.getDetails());
        assertEquals("WARNING", dto.getSeverity());
        assertEquals(created, dto.getCreatedAt());
    }

    @Test
    void fromEntityAllowsNullSeverity() {
        ComplianceAuditLog entity = new ComplianceAuditLog();
        entity.setSeverity(null);

        ComplianceAuditLogDTO dto = ComplianceAuditLogDTO.fromEntity(entity);

        assertNull(dto.getSeverity());
    }
}
