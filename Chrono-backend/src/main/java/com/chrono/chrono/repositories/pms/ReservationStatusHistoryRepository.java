package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {
    List<ReservationStatusHistory> findAllByReservation_IdOrderByChangedAtDesc(Long reservationId);
}
