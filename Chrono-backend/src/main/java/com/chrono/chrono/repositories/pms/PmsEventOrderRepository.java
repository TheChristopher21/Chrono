package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsEventOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PmsEventOrderRepository extends JpaRepository<PmsEventOrder,Long> {
    Optional<PmsEventOrder> findByResourceBooking_Id(Long bookingId);
}
