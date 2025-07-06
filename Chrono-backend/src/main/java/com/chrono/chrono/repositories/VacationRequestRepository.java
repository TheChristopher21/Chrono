package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.entities.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {

    List<VacationRequest> findByUser(User user);

    List<VacationRequest> findByUserAndApprovedTrue(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM VacationRequest vr WHERE vr.user = :user")
    void deleteByUser(@Param("user") User user);

    List<VacationRequest> findByUser_Company_Id(Long companyId);

    List<VacationRequest> findByUser_Company_IdAndApprovedTrue(Long companyId);

}