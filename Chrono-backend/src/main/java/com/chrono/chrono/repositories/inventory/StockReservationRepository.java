package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.StockReservation;
import com.chrono.chrono.entities.inventory.StockReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    List<StockReservation> findAllBySalesOrderLine_IdAndStatus(Long salesOrderLineId, StockReservationStatus status);
    List<StockReservation> findAllBySalesOrderLine_IdInAndStatus(Collection<Long> salesOrderLineIds, StockReservationStatus status);
    List<StockReservation> findAllByCompany_Id(Long companyId);
    List<StockReservation> findAllByCompany_IdAndStatus(Long companyId, StockReservationStatus status);
}
