package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByStatus(SalesOrderStatus status);
    List<SalesOrder> findByStatusAndCompany_Id(SalesOrderStatus status, Long companyId);
    Optional<SalesOrder> findByOrderNumberIgnoreCase(String orderNumber);
    Optional<SalesOrder> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<SalesOrder> findByOrderNumberIgnoreCaseAndCompany_Id(String orderNumber, Long companyId);
    List<SalesOrder> findAllByCompany_Id(Long companyId);
    Page<SalesOrder> findAllByCompany_Id(Long companyId, Pageable pageable);
    List<SalesOrder> findAllByIdInAndCompany_Id(Collection<Long> ids, Long companyId);
}
