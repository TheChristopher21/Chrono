package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.TourismTaxPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface TourismTaxPostingRepository extends JpaRepository<TourismTaxPosting, Long> {
    boolean existsByReservation_Id(Long reservationId);
    List<TourismTaxPosting> findAllByProperty_IdOrderByPostedAtDesc(Long propertyId);
    List<TourismTaxPosting> findAllByProperty_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
            Long propertyId, LocalDateTime from, LocalDateTime toExclusive);
}
