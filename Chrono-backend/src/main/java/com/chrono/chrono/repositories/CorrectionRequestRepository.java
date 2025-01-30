package com.chrono.chrono.repositories;

import com.chrono.chrono.dto.CorrectionRequest; // <-- Wichtig, NICHT .dto
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {
    @Query("SELECT cr FROM CorrectionRequest cr LEFT JOIN FETCH cr.originalTimeTracking WHERE cr.approved = false AND cr.denied = false")
    List<CorrectionRequest> findAllWithOriginalTimes();



    List<CorrectionRequest> findByUser(User user);

    List<CorrectionRequest> findByApprovedFalseAndDeniedFalse();
}
