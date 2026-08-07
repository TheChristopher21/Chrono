package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PosTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface PosTicketRepository extends JpaRepository<PosTicket, Long> {
    List<PosTicket> findAllByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    boolean existsByProperty_IdAndTicketNumberIgnoreCase(Long propertyId, String ticketNumber);
    List<PosTicket> findAllByProperty_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscCreatedAtAsc(
            Long propertyId, LocalDate from, LocalDate toExclusive);
}
