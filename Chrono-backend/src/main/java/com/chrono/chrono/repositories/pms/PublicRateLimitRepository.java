package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PublicRateLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PublicRateLimitRepository extends JpaRepository<PublicRateLimit, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quota from PublicRateLimit quota where quota.rateKey = :rateKey")
    Optional<PublicRateLimit> findByRateKeyForUpdate(@Param("rateKey") String rateKey);

    @Modifying
    @Query("delete from PublicRateLimit quota where quota.updatedAt < :cutoff")
    int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
