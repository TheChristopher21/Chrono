package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsReceivable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
public interface PmsReceivableRepository extends JpaRepository<PmsReceivable,Long> {
    @EntityGraph(attributePaths={"organization","invoice","invoice.folio","invoice.folio.reservation","invoice.folio.reservation.groupBooking"})
    @Query("select r from PmsReceivable r where r.property.id=:propertyId and (:organizationId is null or r.organization.id=:organizationId) and (:openOnly=false or r.amount-r.settledAmount-r.creditedAmount<>0) and (:overdueOnly=false or (r.amount-r.settledAmount-r.creditedAmount>0 and r.dueDate<:date)) and (:query='' or lower(r.organization.name) like :query escape '!' or lower(r.invoice.invoiceNumber) like :query escape '!') order by r.dueDate asc,r.id asc")
    Page<PmsReceivable> searchPage(@Param("propertyId") Long propertyId,@Param("organizationId") Long organizationId,@Param("openOnly") boolean openOnly,@Param("overdueOnly") boolean overdueOnly,@Param("date") LocalDate date,@Param("query") String query,Pageable pageable);
    @Query("select coalesce(sum(case when r.amount-r.settledAmount-r.creditedAmount>0 then r.amount-r.settledAmount-r.creditedAmount else 0 end),0) from PmsReceivable r where r.property.id=:propertyId and r.organization.id=:organizationId")
    BigDecimal sumOutstanding(@Param("propertyId") Long propertyId,@Param("organizationId") Long organizationId);
    interface AccountOutstanding { Long getOrganizationId(); BigDecimal getOutstanding(); }
    @Query("select r.organization.id as organizationId, sum(case when r.amount-r.settledAmount-r.creditedAmount>0 then r.amount-r.settledAmount-r.creditedAmount else 0 end) as outstanding from PmsReceivable r where r.property.id=:propertyId group by r.organization.id")
    List<AccountOutstanding> outstandingByOrganization(@Param("propertyId") Long propertyId);
    interface Summary { BigDecimal getTotalOutstanding(); BigDecimal getTotalOverdue(); }
    @Query("select coalesce(sum(case when r.amount-r.settledAmount-r.creditedAmount>0 then r.amount-r.settledAmount-r.creditedAmount else 0 end),0) as totalOutstanding, coalesce(sum(case when r.amount-r.settledAmount-r.creditedAmount>0 and r.dueDate<:date then r.amount-r.settledAmount-r.creditedAmount else 0 end),0) as totalOverdue from PmsReceivable r where r.property.id=:propertyId")
    Summary summary(@Param("propertyId") Long propertyId,@Param("date") LocalDate date);
    Optional<PmsReceivable> findByInvoice_Id(Long invoiceId);
    Optional<PmsReceivable> findByIdAndProperty_Id(Long id,Long propertyId);
    List<PmsReceivable> findAllByProperty_IdOrderByDueDateAscIdAsc(Long propertyId);
    List<PmsReceivable> findAllByProperty_IdAndOrganization_Id(Long propertyId,Long organizationId);
    List<PmsReceivable> findAllByInvoice_Folio_Id(Long folioId);
}
