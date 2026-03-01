package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmDocument;

import java.time.LocalDateTime;

public class CrmDocumentDTO {
    private final Long id;
    private final String fileName;
    private final String url;
    private final LocalDateTime uploadedAt;
    private final String uploadedBy;

    public CrmDocumentDTO(Long id, String fileName, String url, LocalDateTime uploadedAt, String uploadedBy) {
        this.id = id;
        this.fileName = fileName;
        this.url = url;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    public static CrmDocumentDTO from(CrmDocument document) {
        return new CrmDocumentDTO(
                document.getId(),
                document.getFileName(),
                document.getUrl(),
                document.getUploadedAt(),
                document.getUploadedBy());
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getUrl() {
        return url;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }
}
