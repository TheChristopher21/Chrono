package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsProfileDocument;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface PmsProfileDocumentRepository extends JpaRepository<PmsProfileDocument, Long> {
    interface Metadata {
        Long getId(); Long getOrganizationId(); Long getRatePlanId(); String getFileName(); String getContentType();
        long getSizeBytes(); String getSha256(); String getUploadedBy(); java.time.LocalDateTime getUploadedAt();
    }
    @Query("select d.id as id, d.organization.id as organizationId, r.id as ratePlanId, d.fileName as fileName, "
            + "d.contentType as contentType, d.sizeBytes as sizeBytes, d.sha256 as sha256, d.uploadedBy as uploadedBy, "
            + "d.uploadedAt as uploadedAt from PmsProfileDocument d left join d.ratePlan r "
            + "where d.organization.id = :organizationId and d.company.id = :companyId order by d.uploadedAt desc")
    List<Metadata> findMetadata(Long organizationId, Long companyId);
    @Modifying
    @Query("update PmsProfileDocument d set d.organization = :target where d.company.id = :companyId and d.organization.id = :sourceId")
    void relinkOrganization(Long companyId, Long sourceId, com.chrono.chrono.entities.pms.PmsOrganization target);
    Optional<PmsProfileDocument> findByIdAndCompany_Id(Long id, Long companyId);
    long countByOrganization_Id(Long organizationId);
    @Query("select coalesce(sum(d.sizeBytes), 0) from PmsProfileDocument d where d.company.id = :companyId")
    long storedBytes(Long companyId);
}
