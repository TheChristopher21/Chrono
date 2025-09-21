package com.chrono.chrono.dto;

import com.chrono.chrono.dto.ComplianceAuditLogDTO;
import com.chrono.chrono.entities.ComplianceAuditLog;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field; // Import Field
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplianceAuditLogDTOTest {

    @Test
    void fromEntityCopiesAllScalarFields() throws Exception { // Add "throws Exception"
        ComplianceAuditLog entity = new ComplianceAuditLog();

        // Use Reflection to set the private 'id' field for the test
        Field idField = ComplianceAuditLog.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, 12L);

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

    // ... your other test method remains the same
}