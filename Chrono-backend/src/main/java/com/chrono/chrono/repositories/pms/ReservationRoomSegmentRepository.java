package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.ReservationRoomSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReservationRoomSegmentRepository extends JpaRepository<ReservationRoomSegment, Long> {
    List<ReservationRoomSegment> findAllByReservation_IdOrderByStartDateAsc(Long reservationId);
}
