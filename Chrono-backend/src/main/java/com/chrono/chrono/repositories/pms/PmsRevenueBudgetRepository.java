package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsRevenueBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface PmsRevenueBudgetRepository extends JpaRepository<PmsRevenueBudget,Long> {
 Optional<PmsRevenueBudget> findByProperty_IdAndMonthStart(Long propertyId,LocalDate month);
 List<PmsRevenueBudget> findByProperty_IdAndMonthStartGreaterThanEqualAndMonthStartLessThanOrderByMonthStartAsc(Long propertyId,LocalDate from,LocalDate to);
}
