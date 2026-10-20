package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.UserUiPreference;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserUiPreferenceRepository extends JpaRepository<UserUiPreference, Long> {

    Optional<UserUiPreference> findByUser_IdAndTenantKeyAndAreaAndContextKey(
            Long userId,
            String tenantKey,
            UserUiPreferenceArea area,
            String contextKey
    );
}
