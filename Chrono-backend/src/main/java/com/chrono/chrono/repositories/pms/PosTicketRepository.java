package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PosTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.util.List;
import java.time.LocalDate;

public interface PosTicketRepository extends JpaRepository<PosTicket, Long> {
    @Query("select coalesce(sum(t.grossAmount), 0) from PosTicket t where t.cashShift.id = :shiftId "
            + "and t.status = com.chrono.chrono.entities.pms.PosTicketStatus.SETTLED "
            + "and t.paymentMethod = com.chrono.chrono.entities.pms.PaymentMethod.CASH")
    BigDecimal sumCashByShift(@Param("shiftId") Long shiftId);
    List<PosTicket> findAllByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    boolean existsByProperty_IdAndTicketNumberIgnoreCase(Long propertyId, String ticketNumber);
    List<PosTicket> findAllByProperty_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscCreatedAtAsc(
            Long propertyId, LocalDate from, LocalDate toExclusive);
}
