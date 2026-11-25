package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsDeliveryJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;
public interface PmsDeliveryJobRepository extends JpaRepository<PmsDeliveryJob,Long> {
    interface Scope { Long getPropertyId(); Long getCompanyId(); }
    @Query("select j.property.id as propertyId,j.property.company.id as companyId from PmsDeliveryJob j where j.id=:id")
    Optional<Scope> scope(@Param("id") Long id);
    Optional<PmsDeliveryJob> findByProperty_IdAndRequestKey(Long propertyId,String requestKey);
    Optional<PmsDeliveryJob> findByIdAndProperty_Id(Long id,Long propertyId);
    Page<PmsDeliveryJob> findByProperty_IdOrderByCreatedAtDescIdDesc(Long propertyId,Pageable page);
    @Query("select j.id from PmsDeliveryJob j where (j.status in ('QUEUED','RETRY') and j.nextAttemptAt<=:now) or (j.status='SENDING' and j.leaseUntil<:now) order by j.nextAttemptAt,j.id")
    List<Long> due(@Param("now") LocalDateTime now,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from PmsDeliveryJob j where j.id=:id")
    Optional<PmsDeliveryJob> locked(@Param("id") Long id);
}
