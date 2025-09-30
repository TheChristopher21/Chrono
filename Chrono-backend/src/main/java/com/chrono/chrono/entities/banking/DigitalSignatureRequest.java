package com.chrono.chrono.entities.banking;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "digital_signature_requests")
public class DigitalSignatureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String documentType;

    private String documentPath;

    private String signerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignatureStatus status = SignatureStatus.PENDING;

    private LocalDateTime requestedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    private String providerReference;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public String getSignerEmail() {
        return signerEmail;
    }

    public void setSignerEmail(String signerEmail) {
        this.signerEmail = signerEmail;
    }

    public SignatureStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureStatus status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }
}
