package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.PaymentInstruction;

import java.math.BigDecimal;

public class PaymentInstructionDTO {
    private final Long id;
    private final String creditorName;
    private final String creditorIban;
    private final String creditorBic;
    private final BigDecimal amount;
    private final String currency;
    private final String reference;

    public PaymentInstructionDTO(Long id, String creditorName, String creditorIban, String creditorBic,
                                 BigDecimal amount, String currency, String reference) {
        this.id = id;
        this.creditorName = creditorName;
        this.creditorIban = creditorIban;
        this.creditorBic = creditorBic;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    public static PaymentInstructionDTO from(PaymentInstruction instruction) {
        return new PaymentInstructionDTO(
                instruction.getId(),
                instruction.getCreditorName(),
                instruction.getCreditorIban(),
                instruction.getCreditorBic(),
                instruction.getAmount(),
                instruction.getCurrency(),
                instruction.getReference());
    }

    public Long getId() {
        return id;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public String getCreditorBic() {
        return creditorBic;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }
}
