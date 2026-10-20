package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name = "pms_profile_documents")
public class PmsProfileDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id", nullable = false) private PmsOrganization organization;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rate_plan_id") private RatePlan ratePlan;
    @Column(name = "file_name", nullable = false, length = 180) private String fileName;
    @Column(name = "content_type", nullable = false, length = 80) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "uploaded_by", nullable = false, length = 120) private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false) private LocalDateTime uploadedAt;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(nullable = false, columnDefinition = "longblob") private byte[] content;
    @Column(name = "storage_key", length = 100) private String storageKey;
    @Column(name = "version_group", length = 36) private String versionGroup;
    @Column(name = "document_version", nullable = false) private int documentVersion = 1;
    @Column(nullable = false) private boolean archived;
}
