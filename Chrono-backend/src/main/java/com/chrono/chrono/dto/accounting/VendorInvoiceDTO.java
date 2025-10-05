package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;

public class VendorInvoiceDTO {
    private final Long id;
    private final String invoiceNumber;
    private final String vendorName;
    private final BigDecimal amount;
    private final BigDecimal amountPaid;
    private final BigDecimal openAmount;
    private final String currency;
    private final InvoiceStatus status;
    private final LocalDate invoiceDate;
    private final LocalDate dueDate;
    private final LocalDate lastPaymentDate;

    public VendorInvoiceDTO(Long id, String invoiceNumber, String vendorName, BigDecimal amount,
                            BigDecimal amountPaid, BigDecimal openAmount, String currency, InvoiceStatus status,
                            LocalDate invoiceDate, LocalDate dueDate, LocalDate lastPaymentDate) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.vendorName = vendorName;
        this.amount = amount;
        this.amountPaid = amountPaid;
        this.openAmount = openAmount;
        this.currency = currency;
        this.status = status;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.lastPaymentDate = lastPaymentDate;
    }

    public static VendorInvoiceDTO from(VendorInvoice invoice) {
        return new VendorInvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getVendorName(),
                invoice.getAmount(),
                invoice.getAmountPaid(),
                invoice.getOpenAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getLastPaymentDate());
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getOpenAmount() {
        return openAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }
}
