package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.ReservationGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReservationGuestRepository extends JpaRepository<ReservationGuest, Long> {
    List<ReservationGuest> findAllByGuest_Id(Long guestId);
}
