package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PmsPaymentRequestRepository extends JpaRepository<PmsPaymentRequest, Long> {
    Optional<PmsPaymentRequest> findByFolio_IdAndRequestSourceAndClientRequestKey(Long folioId, String source, String clientKey);
    Optional<PmsPaymentRequest> findByAutomaticReservationIdAndFolio_Reservation_Property_IdAndFolio_Reservation_Property_Company_Id(Long reservationId, Long propertyId, Long companyId);
    /** Only provider metadata may use this global, immutable identity. Never use a browser request ID here. */
    @org.springframework.data.jpa.repository.Query("select r from PmsPaymentRequest r where r.requestKey = :key")
    Optional<PmsPaymentRequest> findByProviderRequestKey(@org.springframework.data.repository.query.Param("key") String key);
    List<PmsPaymentRequest> findAllByFolio_IdOrderByCreatedAtDesc(Long folioId);
    Optional<PmsPaymentRequest> findByProviderSessionId(String id);
    Optional<PmsPaymentRequest> findByProviderPaymentIntentId(String id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio","folio.reservation","folio.reservation.property","folio.reservation.property.company"})
    List<PmsPaymentRequest> findTop50ByStatusInOrderByLastCheckedAtAscIdAsc(List<String> statuses);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"folio","folio.reservation"})
    List<PmsPaymentRequest> findTop100ByFolio_Reservation_Property_IdOrderByCreatedAtDesc(Long propertyId);
}
