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
    private final Long vendorInvoiceId;
    private final Long customerInvoiceId;
    private final String vendorInvoiceNumber;
    private final String customerInvoiceNumber;

    public PaymentInstructionDTO(Long id, String creditorName, String creditorIban, String creditorBic,
                                 BigDecimal amount, String currency, String reference,
                                 Long vendorInvoiceId, Long customerInvoiceId,
                                 String vendorInvoiceNumber, String customerInvoiceNumber) {
        this.id = id;
        this.creditorName = creditorName;
        this.creditorIban = creditorIban;
        this.creditorBic = creditorBic;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.vendorInvoiceId = vendorInvoiceId;
        this.customerInvoiceId = customerInvoiceId;
        this.vendorInvoiceNumber = vendorInvoiceNumber;
        this.customerInvoiceNumber = customerInvoiceNumber;
    }

    public static PaymentInstructionDTO from(PaymentInstruction instruction) {
        return new PaymentInstructionDTO(
                instruction.getId(),
                instruction.getCreditorName(),
                instruction.getCreditorIban(),
                instruction.getCreditorBic(),
                instruction.getAmount(),
                instruction.getCurrency(),
                instruction.getReference(),
                instruction.getVendorInvoice() != null ? instruction.getVendorInvoice().getId() : null,
                instruction.getCustomerInvoice() != null ? instruction.getCustomerInvoice().getId() : null,
                instruction.getVendorInvoice() != null ? instruction.getVendorInvoice().getInvoiceNumber() : null,
                instruction.getCustomerInvoice() != null ? instruction.getCustomerInvoice().getInvoiceNumber() : null);
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

    public Long getVendorInvoiceId() {
        return vendorInvoiceId;
    }

    public Long getCustomerInvoiceId() {
        return customerInvoiceId;
    }

    public String getVendorInvoiceNumber() {
        return vendorInvoiceNumber;
    }

    public String getCustomerInvoiceNumber() {
        return customerInvoiceNumber;
    }
}
