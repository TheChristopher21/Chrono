package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.StockLevel;
import com.chrono.chrono.entities.inventory.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    @Query(value = "SELECT * FROM inv_stock_levels WHERE product_id = :productId AND warehouse_id = :warehouseId ORDER BY id LIMIT 1", nativeQuery = true)
    Optional<StockLevel> findFirstLegacyBalance(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    default Optional<StockLevel> findByProductAndWarehouse(Product product, Warehouse warehouse) {
        return findFirstLegacyBalance(product.getId(), warehouse.getId());
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.product = :product and s.warehouse = :warehouse and s.inventoryKey = :inventoryKey")
    Optional<StockLevel> findForUpdate(@Param("product") Product product,
                                       @Param("warehouse") Warehouse warehouse,
                                       @Param("inventoryKey") String inventoryKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from StockLevel s
            where s.product = :product
              and s.warehouse = :warehouse
              and s.inventoryStatus = com.chrono.chrono.entities.inventory.InventoryStatus.AVAILABLE
              and s.quantity > s.reservedQuantity
            order by case when s.expirationDate is null then 1 else 0 end,
                     s.expirationDate asc,
                     s.warehouseBin.pickSequence asc,
                     s.id asc
            """)
    List<StockLevel> findAvailableForReservation(@Param("product") Product product,
                                                  @Param("warehouse") Warehouse warehouse);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from StockLevel s
            where s.product = :product and s.warehouse = :warehouse and s.quantity > s.reservedQuantity
            order by case when s.expirationDate is null then 1 else 0 end,
                     s.expirationDate asc, s.warehouseBin.pickSequence asc, s.id asc
            """)
    List<StockLevel> findAllIssuableForUpdate(@Param("product") Product product,
                                               @Param("warehouse") Warehouse warehouse);

    List<StockLevel> findByProduct(Product product);

    List<StockLevel> findAllByWarehouseBin_Id(Long warehouseBinId);

    List<StockLevel> findAllByProduct_Company_Id(Long companyId);

    Page<StockLevel> findAllByProduct_Company_Id(Long companyId, Pageable pageable);
}
