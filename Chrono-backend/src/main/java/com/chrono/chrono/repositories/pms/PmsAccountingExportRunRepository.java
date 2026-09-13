package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsAccountingExportRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PmsAccountingExportRunRepository extends JpaRepository<PmsAccountingExportRun,Long> {
 Optional<PmsAccountingExportRun> findByProperty_IdAndRequestKey(Long propertyId,String key);
 Optional<PmsAccountingExportRun> findByIdAndProperty_Id(Long id,Long propertyId);
 List<PmsAccountingExportRun> findTop50ByProperty_IdOrderByIdDesc(Long propertyId);
}
