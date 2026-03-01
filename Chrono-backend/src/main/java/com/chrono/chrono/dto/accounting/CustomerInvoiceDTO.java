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
    private final BigDecimal amountPaid;
    private final BigDecimal openAmount;
    private final String currency;
    private final InvoiceStatus status;
    private final LocalDate invoiceDate;
    private final LocalDate dueDate;
    private final LocalDate lastPaymentDate;

    public CustomerInvoiceDTO(Long id, String invoiceNumber, String customerName, BigDecimal amount,
                              BigDecimal amountPaid, BigDecimal openAmount, String currency, InvoiceStatus status,
                              LocalDate invoiceDate, LocalDate dueDate, LocalDate lastPaymentDate) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customerName = customerName;
        this.amount = amount;
        this.amountPaid = amountPaid;
        this.openAmount = openAmount;
        this.currency = currency;
        this.status = status;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.lastPaymentDate = lastPaymentDate;
    }

    public static CustomerInvoiceDTO from(CustomerInvoice invoice) {
        return new CustomerInvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerName(),
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

    public String getCustomerName() {
        return customerName;
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
