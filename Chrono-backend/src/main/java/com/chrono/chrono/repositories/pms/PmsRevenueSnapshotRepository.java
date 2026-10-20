package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsRevenueSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface PmsRevenueSnapshotRepository extends JpaRepository<PmsRevenueSnapshot,Long> {
 Optional<PmsRevenueSnapshot> findByProperty_IdAndAsOfDate(Long propertyId,LocalDate date);
 Optional<PmsRevenueSnapshot> findByIdAndProperty_Id(Long id,Long propertyId);
 List<PmsRevenueSnapshot> findTop366ByProperty_IdOrderByAsOfDateDesc(Long propertyId);
}
