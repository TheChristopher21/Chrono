package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByFolio_IdOrderByReceivedAtAsc(Long folioId);
    boolean existsByFolio_IdAndStatusAndAmountGreaterThan(Long folioId, PaymentStatus status, BigDecimal amount);
    List<Payment> findAllByOriginalPayment_IdAndStatus(Long originalPaymentId, PaymentStatus status);
    List<Payment> findAllByFolio_Reservation_Property_IdAndMethodAndStatusAndReceivedAtGreaterThanEqual(
            Long propertyId,
            com.chrono.chrono.entities.pms.PaymentMethod method,
            PaymentStatus status,
            LocalDateTime receivedAt
    );
}
