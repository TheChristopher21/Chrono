package com.chrono.chrono.repositories;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {
    @Query("SELECT cr FROM CorrectionRequest cr LEFT JOIN FETCH cr.targetEntry WHERE cr.approved = false AND cr.denied = false")
    List<CorrectionRequest> findAllWithOriginalTimes();

    List<CorrectionRequest> findByUser(User user);

    @Query("SELECT cr FROM CorrectionRequest cr JOIN FETCH cr.user u WHERE u.company.id = :companyId")
    List<CorrectionRequest> findAllByCompanyId(@Param("companyId") Long companyId);
    @Query("SELECT cr FROM CorrectionRequest cr LEFT JOIN FETCH cr.user u LEFT JOIN FETCH cr.targetEntry WHERE u.username = :username ORDER BY cr.requestDate DESC")
    List<CorrectionRequest> findByUserWithDetails(@Param("username") String username);
    List<CorrectionRequest> findByUserAndDesiredTimestampBetweenAndApprovedIsFalseAndDeniedIsFalse(User user, LocalDateTime start, LocalDateTime end);

    List<CorrectionRequest> findByApprovedFalseAndDeniedFalse();
    @Modifying
    @Transactional
    @Query("DELETE FROM CorrectionRequest cr WHERE cr.user = :user")
    void deleteByUser(@Param("user") User user);
}
