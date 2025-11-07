package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public class PaymentBatchDTO {
    private final Long id;
    private final Long bankAccountId;
    private final PaymentStatus status;
    private final LocalDateTime approvedAt;
    private final String approvalBy;
    private final LocalDateTime transmittedAt;
    private final String transmissionReference;
    private final String providerStatus;
    private final String providerMessage;
    private final List<PaymentInstructionDTO> instructions;

    public PaymentBatchDTO(Long id, Long bankAccountId, PaymentStatus status, LocalDateTime approvedAt,
                           String approvalBy, LocalDateTime transmittedAt, String transmissionReference,
                           String providerStatus, String providerMessage,
                           List<PaymentInstructionDTO> instructions) {
        this.id = id;
        this.bankAccountId = bankAccountId;
        this.status = status;
        this.approvedAt = approvedAt;
        this.approvalBy = approvalBy;
        this.transmittedAt = transmittedAt;
        this.transmissionReference = transmissionReference;
        this.providerStatus = providerStatus;
        this.providerMessage = providerMessage;
        this.instructions = instructions;
    }

    public static PaymentBatchDTO from(PaymentBatch batch) {
        return new PaymentBatchDTO(
                batch.getId(),
                batch.getBankAccount() != null ? batch.getBankAccount().getId() : null,
                batch.getStatus(),
                batch.getApprovedAt(),
                batch.getApprovalBy(),
                batch.getTransmittedAt(),
                batch.getTransmissionReference(),
                batch.getProviderStatus(),
                batch.getProviderMessage(),
                batch.getInstructions().stream().map(PaymentInstructionDTO::from).toList());
    }

    public Long getId() {
        return id;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getApprovalBy() {
        return approvalBy;
    }

    public LocalDateTime getTransmittedAt() {
        return transmittedAt;
    }

    public String getTransmissionReference() {
        return transmissionReference;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderMessage() {
        return providerMessage;
    }

    public List<PaymentInstructionDTO> getInstructions() {
        return instructions;
    }
}
