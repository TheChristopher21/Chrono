package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository  // 🔥 WICHTIG! Damit Spring das Repository erkennt
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}
