package com.chrono.chrono.dto.banking;

import java.util.ArrayList;
import java.util.List;

public class CreatePaymentBatchRequest {
    private Long bankAccountId;
    private List<PaymentInstructionRequest> instructions = new ArrayList<>();

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public List<PaymentInstructionRequest> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<PaymentInstructionRequest> instructions) {
        this.instructions = instructions;
    }
}
