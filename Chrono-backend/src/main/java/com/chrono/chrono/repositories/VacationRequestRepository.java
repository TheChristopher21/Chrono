package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {

    List<VacationRequest> findByUser(User user);

    // Optional: findByApprovedFalseAndDeniedFalse (wenn du offene Anträge willst)
}
