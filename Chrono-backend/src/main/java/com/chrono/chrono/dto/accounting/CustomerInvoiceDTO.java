package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CustomerInvoiceDTO {
    private final Long id;
    private final String invoiceNumber;
    private final String customerName;
    private final BigDecimal amount;
    private final String currency;
    private final InvoiceStatus status;
    private final LocalDate invoiceDate;
    private final LocalDate dueDate;

    public CustomerInvoiceDTO(Long id, String invoiceNumber, String customerName, BigDecimal amount,
                              String currency, InvoiceStatus status, LocalDate invoiceDate, LocalDate dueDate) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customerName = customerName;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
    }

    public static CustomerInvoiceDTO from(CustomerInvoice invoice) {
        return new CustomerInvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerName(),
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

    public String getCustomerName() {
        return customerName;
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
