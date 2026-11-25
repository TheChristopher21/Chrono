package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsRateInheritance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PmsRateInheritanceRepository extends JpaRepository<PmsRateInheritance,Long> {
 Optional<PmsRateInheritance> findByTarget_Id(Long targetId);
 List<PmsRateInheritance> findByTarget_Property_IdOrderByIdAsc(Long propertyId);
}
