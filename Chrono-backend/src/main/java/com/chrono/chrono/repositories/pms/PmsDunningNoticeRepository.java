package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsDunningNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface PmsDunningNoticeRepository extends JpaRepository<PmsDunningNotice,Long> {
 Optional<PmsDunningNotice> findTopByReceivable_IdOrderByLevelDesc(Long id);
 Page<PmsDunningNotice> findByProperty_IdOrderByIdDesc(Long propertyId,Pageable page);
}
