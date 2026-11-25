package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByFolio_IdOrderByReceivedAtAsc(Long folioId);
    List<Payment> findAllByFolio_IdInOrderByReceivedAtAsc(List<Long> folioIds);
    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
    Optional<Payment> findByRefundRequestId(String requestId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio","folio.reservation","folio.reservation.property","folio.reservation.property.company","originalPayment"})
    List<Payment> findTop50ByStatusAndKindOrderByProviderCheckedAtAscIdAsc(PaymentStatus status,com.chrono.chrono.entities.pms.PaymentKind kind);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio"})
    List<Payment> findTop100ByFolio_Reservation_Property_IdAndMethodAndKindAndMerchantContextIsNullOrderByIdDesc(Long propertyId,com.chrono.chrono.entities.pms.PaymentMethod method,com.chrono.chrono.entities.pms.PaymentKind kind);
    boolean existsByCashShift_IdAndStatus(Long shiftId, PaymentStatus status);
    List<Payment> findAllByCashShift_IdAndMethodAndStatus(Long shiftId, com.chrono.chrono.entities.pms.PaymentMethod method, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment from Payment payment
            where payment.id = :paymentId
              and payment.folio.reservation.property.id = :propertyId
              and payment.folio.reservation.property.company.id = :companyId
            """)
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId,
                                        @Param("propertyId") Long propertyId,
                                        @Param("companyId") Long companyId);
    boolean existsByFolio_IdAndStatusAndAmountGreaterThan(Long folioId, PaymentStatus status, BigDecimal amount);
    List<Payment> findAllByOriginalPayment_IdAndStatus(Long originalPaymentId, PaymentStatus status);
    List<Payment> findAllByFolio_Reservation_Property_IdAndMethodAndStatusAndReceivedAtGreaterThanEqual(
            Long propertyId,
            com.chrono.chrono.entities.pms.PaymentMethod method,
            PaymentStatus status,
            LocalDateTime receivedAt
    );
    List<Payment> findAllByFolio_Reservation_Property_IdAndReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtAsc(
            Long propertyId, LocalDateTime from, LocalDateTime toExclusive);
    @Query("""
            select p from Payment p where p.folio.reservation.property.id=:propertyId
              and coalesce(p.postingDate,cast(p.receivedAt as LocalDate)) >= :from
              and coalesce(p.postingDate,cast(p.receivedAt as LocalDate)) < :toExclusive
            order by coalesce(p.postingDate,cast(p.receivedAt as LocalDate)),p.id
            """)
    List<Payment> findByPostingDateRange(@Param("propertyId") Long propertyId,
            @Param("from") java.time.LocalDate from, @Param("toExclusive") java.time.LocalDate toExclusive);
}
