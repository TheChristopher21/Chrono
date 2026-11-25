package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsBankImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PmsBankImportRepository extends JpaRepository<PmsBankImport,Long> {
 Optional<PmsBankImport> findByProperty_IdAndFileHash(Long propertyId,String hash);
 Optional<PmsBankImport> findByIdAndProperty_Id(Long id,Long propertyId);
 List<PmsBankImport> findTop50ByProperty_IdOrderByIdDesc(Long propertyId);
}
