package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PmsPaymentRequestRepository extends JpaRepository<PmsPaymentRequest, Long> {
    Optional<PmsPaymentRequest> findByRequestKey(String requestKey);
    List<PmsPaymentRequest> findAllByFolio_IdOrderByCreatedAtDesc(Long folioId);
    Optional<PmsPaymentRequest> findByProviderSessionId(String id);
    Optional<PmsPaymentRequest> findByProviderPaymentIntentId(String id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio","folio.reservation","folio.reservation.property","folio.reservation.property.company"})
    List<PmsPaymentRequest> findTop50ByStatusInOrderByLastCheckedAtAscIdAsc(List<String> statuses);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio","folio.reservation"})
    List<PmsPaymentRequest> findTop100ByFolio_Reservation_Property_IdOrderByCreatedAtDesc(Long propertyId);
}
