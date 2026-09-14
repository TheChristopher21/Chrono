package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsProfileDocument;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface PmsProfileDocumentRepository extends JpaRepository<PmsProfileDocument, Long> {
    interface Metadata {
        Long getId(); Long getOrganizationId(); Long getRatePlanId(); String getFileName(); String getContentType();
        long getSizeBytes(); String getSha256(); String getUploadedBy(); java.time.LocalDateTime getUploadedAt();
        String getVersionGroup(); int getDocumentVersion(); boolean getArchived();
    }
    @Query("select d.id as id, d.organization.id as organizationId, r.id as ratePlanId, d.fileName as fileName, "
            + "d.contentType as contentType, d.sizeBytes as sizeBytes, d.sha256 as sha256, d.uploadedBy as uploadedBy, "
            + "d.uploadedAt as uploadedAt, d.versionGroup as versionGroup, d.documentVersion as documentVersion, d.archived as archived from PmsProfileDocument d left join d.ratePlan r "
            + "where d.organization.id = :organizationId and d.company.id = :companyId and d.archived = false order by d.uploadedAt desc")
    List<Metadata> findMetadata(Long organizationId, Long companyId);
    @Query("select d.id as id, d.organization.id as organizationId, r.id as ratePlanId, d.fileName as fileName, "
            + "d.contentType as contentType, d.sizeBytes as sizeBytes, d.sha256 as sha256, d.uploadedBy as uploadedBy, "
            + "d.uploadedAt as uploadedAt, d.versionGroup as versionGroup, d.documentVersion as documentVersion, d.archived as archived from PmsProfileDocument d left join d.ratePlan r "
            + "where d.organization.id = :organizationId and d.company.id = :companyId "
            + "and d.archived = false and (r is null or r.property.id = :propertyId) order by d.uploadedAt desc")
    List<Metadata> findMetadataForProperty(Long organizationId, Long companyId, Long propertyId);
    @Modifying
    @Query("update PmsProfileDocument d set d.organization = :target where d.company.id = :companyId and d.organization.id = :sourceId")
    void relinkOrganization(Long companyId, Long sourceId, com.chrono.chrono.entities.pms.PmsOrganization target);
    Optional<PmsProfileDocument> findByIdAndCompany_Id(Long id, Long companyId);
    long countByOrganization_Id(Long organizationId);
    @Query("select coalesce(sum(d.sizeBytes), 0) from PmsProfileDocument d where d.company.id = :companyId")
    long storedBytes(Long companyId);
    @Query("select coalesce(max(d.documentVersion), 0) from PmsProfileDocument d where d.company.id = :companyId and d.versionGroup = :versionGroup")
    int latestVersion(Long companyId, String versionGroup);
}
