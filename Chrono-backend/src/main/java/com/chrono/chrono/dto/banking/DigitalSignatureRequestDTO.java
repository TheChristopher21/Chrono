package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.DigitalSignatureRequest;
import com.chrono.chrono.entities.banking.SignatureStatus;

import java.time.LocalDateTime;

public class DigitalSignatureRequestDTO {
    private final Long id;
    private final String documentType;
    private final String documentPath;
    private final String signerEmail;
    private final SignatureStatus status;
    private final String providerReference;
    private final LocalDateTime completedAt;

    public DigitalSignatureRequestDTO(Long id, String documentType, String documentPath, String signerEmail,
                                      SignatureStatus status, String providerReference, LocalDateTime completedAt) {
        this.id = id;
        this.documentType = documentType;
        this.documentPath = documentPath;
        this.signerEmail = signerEmail;
        this.status = status;
        this.providerReference = providerReference;
        this.completedAt = completedAt;
    }

    public static DigitalSignatureRequestDTO from(DigitalSignatureRequest request) {
        return new DigitalSignatureRequestDTO(
                request.getId(),
                request.getDocumentType(),
                request.getDocumentPath(),
                request.getSignerEmail(),
                request.getStatus(),
                request.getProviderReference(),
                request.getCompletedAt());
    }

    public Long getId() {
        return id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public String getSignerEmail() {
        return signerEmail;
    }

    public SignatureStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
