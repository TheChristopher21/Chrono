package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsReceivableSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
public interface PmsReceivableSettlementRepository extends JpaRepository<PmsReceivableSettlement,Long> {
    Optional<PmsReceivableSettlement> findByProperty_IdAndRequestId(Long propertyId,String requestId);
    List<PmsReceivableSettlement> findAllByReceivable_IdOrderByIdAsc(Long receivableId);
    List<PmsReceivableSettlement> findAllByProperty_IdAndPostingDateGreaterThanEqualAndPostingDateLessThanOrderByPostingDateAscIdAsc(Long propertyId,LocalDate from,LocalDate toExclusive);
}
