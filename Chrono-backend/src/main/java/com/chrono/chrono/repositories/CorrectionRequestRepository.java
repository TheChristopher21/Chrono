package com.chrono.chrono.repositories;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {
    @Query("SELECT cr FROM CorrectionRequest cr LEFT JOIN FETCH cr.originalTimeTracking WHERE cr.approved = false AND cr.denied = false")
    List<CorrectionRequest> findAllWithOriginalTimes();

    List<CorrectionRequest> findByUser(User user);

    List<CorrectionRequest> findByApprovedFalseAndDeniedFalse();
    @Modifying
    @Transactional
    @Query("DELETE FROM CorrectionRequest cr WHERE cr.user = :user")
    void deleteByUser(@Param("user") User user);
}
