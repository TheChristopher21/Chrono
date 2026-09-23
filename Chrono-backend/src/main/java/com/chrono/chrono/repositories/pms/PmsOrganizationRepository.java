package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PmsOrganizationRepository extends JpaRepository<PmsOrganization, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"parentOrganization","mergedInto"})
    @org.springframework.data.jpa.repository.Query("select o from PmsOrganization o where o.company.id=:companyId and (:activeOnly=false or o.active=true) and (:masterOnly=false or o.masterRecord=true) and (:query='' or lower(o.name) like :query escape '!' or lower(o.referenceCode) like :query escape '!' or lower(o.vatNumber) like :query escape '!') order by o.name asc,o.id asc")
    org.springframework.data.domain.Page<PmsOrganization> searchDirectory(@org.springframework.data.repository.query.Param("companyId") Long companyId,@org.springframework.data.repository.query.Param("query") String query,@org.springframework.data.repository.query.Param("activeOnly") boolean activeOnly,@org.springframework.data.repository.query.Param("masterOnly") boolean masterOnly,org.springframework.data.domain.Pageable pageable);
    List<PmsOrganization> findAllByCompany_IdOrderByNameAsc(Long companyId);
    List<PmsOrganization> findAllByParentOrganization_Id(Long parentOrganizationId);
    Optional<PmsOrganization> findByIdAndCompany_Id(Long id, Long companyId);
}
