package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.CorrectionRequest; // <-- Wichtig, NICHT .dto
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {

    List<CorrectionRequest> findByUser(User user);

    List<CorrectionRequest> findByApprovedFalseAndDeniedFalse();
}
