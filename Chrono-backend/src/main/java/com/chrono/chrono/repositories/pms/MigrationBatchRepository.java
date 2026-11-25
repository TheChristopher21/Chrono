package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.MigrationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationBatchRepository extends JpaRepository<MigrationBatch, Long> {
    List<MigrationBatch> findAllByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    Optional<MigrationBatch> findByProperty_IdAndIdempotencyKey(Long propertyId, String idempotencyKey);
}
