package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsEventOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PmsEventOfferRepository extends JpaRepository<PmsEventOffer,Long> {
 List<PmsEventOffer> findTop100ByBooking_IdOrderByOfferVersionDesc(Long bookingId);
 Optional<PmsEventOffer> findByTokenHash(String tokenHash);
 Optional<PmsEventOffer> findFirstByBooking_IdOrderByOfferVersionDesc(Long bookingId);
 Optional<PmsEventOffer> findFirstByBooking_IdAndStatusOrderByOfferVersionDesc(Long bookingId,String status);
}
