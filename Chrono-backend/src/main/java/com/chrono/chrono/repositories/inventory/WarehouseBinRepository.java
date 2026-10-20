package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.WarehouseBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, Long> {
    List<WarehouseBin> findAllByWarehouse_Company_IdOrderByWarehouse_CodeAscPickSequenceAscCodeAsc(Long companyId);
    List<WarehouseBin> findAllByWarehouse_IdOrderByPickSequenceAscCodeAsc(Long warehouseId);
    Optional<WarehouseBin> findByIdAndWarehouse_Company_Id(Long id, Long companyId);
    Optional<WarehouseBin> findByWarehouse_IdAndCodeIgnoreCase(Long warehouseId, String code);
    Optional<WarehouseBin> findByBarcodeAndWarehouse_Company_Id(String barcode, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WarehouseBin b where b.id = :id")
    Optional<WarehouseBin> findForUpdate(@Param("id") Long id);
}
