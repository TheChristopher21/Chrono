package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.UserAudit;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditRepository extends JpaRepository<UserAudit, Long> {
    void deleteByUser(User user);
}
