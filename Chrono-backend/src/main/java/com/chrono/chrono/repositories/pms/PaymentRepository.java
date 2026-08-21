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
}
