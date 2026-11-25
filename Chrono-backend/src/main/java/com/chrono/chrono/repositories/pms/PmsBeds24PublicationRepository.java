package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsBeds24Publication;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;import java.time.LocalDateTime;
public interface PmsBeds24PublicationRepository extends JpaRepository<PmsBeds24Publication,Long>{
 Optional<PmsBeds24Publication> findByPropertyIdAndRequestKey(Long propertyId,String key);
 boolean existsByPropertyIdAndStatusIn(Long propertyId,List<String> statuses);
 boolean existsByPropertyIdAndIdGreaterThan(Long propertyId,Long id);
 List<PmsBeds24Publication> findTop30ByPropertyIdOrderByIdDesc(Long propertyId);
 List<PmsBeds24Publication> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(List<String> statuses,LocalDateTime now);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select p from PmsBeds24Publication p where p.id=:id") Optional<PmsBeds24Publication> findLocked(@Param("id") Long id);
}
