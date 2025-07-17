package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.UserAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditRepository extends JpaRepository<UserAudit, Long> {
}
