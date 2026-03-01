package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ReadAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadAccessAuditRepository extends JpaRepository<ReadAccessAudit, Long> {
}
