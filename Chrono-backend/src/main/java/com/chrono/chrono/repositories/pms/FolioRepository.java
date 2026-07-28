package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Folio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolioRepository extends JpaRepository<Folio, Long> {
    Optional<Folio> findFirstByReservation_IdOrderByIdAsc(Long reservationId);
    List<Folio> findAllByReservation_IdOrderByIdAsc(Long reservationId);
    Optional<Folio> findByIdAndReservation_Property_Company_Id(Long id, Long companyId);
    List<Folio> findAllByReservation_Property_IdOrderByCreatedAtDesc(Long propertyId);
}
