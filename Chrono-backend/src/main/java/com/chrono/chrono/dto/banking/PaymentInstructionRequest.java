package com.chrono.chrono.dto.banking;

import java.math.BigDecimal;

public class PaymentInstructionRequest {
    private Long vendorInvoiceId;
    private Long customerInvoiceId;
    private String creditorName;
    private String creditorIban;
    private String creditorBic;
    private BigDecimal amount;
    private String currency;
    private String reference;

    public Long getVendorInvoiceId() {
        return vendorInvoiceId;
    }

    public void setVendorInvoiceId(Long vendorInvoiceId) {
        this.vendorInvoiceId = vendorInvoiceId;
    }

    public Long getCustomerInvoiceId() {
        return customerInvoiceId;
    }

    public void setCustomerInvoiceId(Long customerInvoiceId) {
        this.customerInvoiceId = customerInvoiceId;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public void setCreditorIban(String creditorIban) {
        this.creditorIban = creditorIban;
    }

    public String getCreditorBic() {
        return creditorBic;
    }

    public void setCreditorBic(String creditorBic) {
        this.creditorBic = creditorBic;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
