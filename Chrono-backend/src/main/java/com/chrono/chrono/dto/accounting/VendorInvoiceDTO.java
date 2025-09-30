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
    private final String currency;
    private final InvoiceStatus status;
    private final LocalDate invoiceDate;
    private final LocalDate dueDate;

    public VendorInvoiceDTO(Long id, String invoiceNumber, String vendorName, BigDecimal amount,
                            String currency, InvoiceStatus status, LocalDate invoiceDate, LocalDate dueDate) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.vendorName = vendorName;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
    }

    public static VendorInvoiceDTO from(VendorInvoice invoice) {
        return new VendorInvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getVendorName(),
                invoice.getAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getInvoiceDate(),
                invoice.getDueDate());
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
}
