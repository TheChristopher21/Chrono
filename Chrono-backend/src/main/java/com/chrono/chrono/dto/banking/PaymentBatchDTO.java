package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PaymentBatchDTO {
    private final Long id;
    private final Long bankAccountId;
    private final String bankAccountName;
    private final String bankAccountIban;
    private final PaymentStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime approvedAt;
    private final String approvalBy;
    private final LocalDateTime transmittedAt;
    private final String transmissionReference;
    private final String providerStatus;
    private final String providerMessage;
    private final String deliveryChannel;
    private final String providerArtifactPath;
    private final String providerArtifactName;
    private final BigDecimal totalAmount;
    private final List<PaymentInstructionDTO> instructions;

    public PaymentBatchDTO(Long id, Long bankAccountId, String bankAccountName, String bankAccountIban,
                           PaymentStatus status, LocalDateTime createdAt, LocalDateTime approvedAt,
                           String approvalBy, LocalDateTime transmittedAt, String transmissionReference,
                           String providerStatus, String providerMessage, String deliveryChannel,
                           String providerArtifactPath, String providerArtifactName, BigDecimal totalAmount,
                           List<PaymentInstructionDTO> instructions) {
        this.id = id;
        this.bankAccountId = bankAccountId;
        this.bankAccountName = bankAccountName;
        this.bankAccountIban = bankAccountIban;
        this.status = status;
        this.createdAt = createdAt;
        this.approvedAt = approvedAt;
        this.approvalBy = approvalBy;
        this.transmittedAt = transmittedAt;
        this.transmissionReference = transmissionReference;
        this.providerStatus = providerStatus;
        this.providerMessage = providerMessage;
        this.deliveryChannel = deliveryChannel;
        this.providerArtifactPath = providerArtifactPath;
        this.providerArtifactName = providerArtifactName;
        this.totalAmount = totalAmount;
        this.instructions = instructions;
    }

    public static PaymentBatchDTO from(PaymentBatch batch) {
        BigDecimal totalAmount = batch.getInstructions().stream()
                .map(PaymentInstructionDTO::from)
                .map(PaymentInstructionDTO::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentInstructionDTO> instructionDtos = batch.getInstructions().stream()
                .map(PaymentInstructionDTO::from)
                .toList();
        return new PaymentBatchDTO(
                batch.getId(),
                batch.getBankAccount() != null ? batch.getBankAccount().getId() : null,
                batch.getBankAccount() != null ? batch.getBankAccount().getName() : null,
                batch.getBankAccount() != null ? batch.getBankAccount().getIban() : null,
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getApprovedAt(),
                batch.getApprovalBy(),
                batch.getTransmittedAt(),
                batch.getTransmissionReference(),
                batch.getProviderStatus(),
                batch.getProviderMessage(),
                batch.getDeliveryChannel(),
                batch.getProviderArtifactPath(),
                batch.getProviderArtifactName(),
                totalAmount,
                instructionDtos);
    }

    public Long getId() {
        return id;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public String getBankAccountIban() {
        return bankAccountIban;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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

    public String getDeliveryChannel() {
        return deliveryChannel;
    }

    public String getProviderArtifactPath() {
        return providerArtifactPath;
    }

    public String getProviderArtifactName() {
        return providerArtifactName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<PaymentInstructionDTO> getInstructions() {
        return instructions;
    }
}
