package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsEventOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PmsEventOrderLineRepository extends JpaRepository<PmsEventOrderLine,Long> {
    List<PmsEventOrderLine> findAllByEventOrder_IdOrderByIdAsc(Long orderId);
}
