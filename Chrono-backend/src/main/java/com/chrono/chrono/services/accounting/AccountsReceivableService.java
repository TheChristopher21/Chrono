package com.chrono.chrono.services.accounting;

import com.chrono.chrono.dto.InvoiceSummaryDTO;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.accounting.*;
import com.chrono.chrono.repositories.accounting.CustomerInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountsReceivableService {

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final AccountingService accountingService;

    public AccountsReceivableService(CustomerInvoiceRepository customerInvoiceRepository,
                                     AccountingService accountingService) {
        this.customerInvoiceRepository = customerInvoiceRepository;
        this.accountingService = accountingService;
    }

    @Transactional
    public CustomerInvoice recordProjectInvoice(Project project, InvoiceSummaryDTO summary) {
        if (summary == null) {
            throw new IllegalArgumentException("Invoice summary required");
        }
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setCustomerName(summary.getCustomerName());
        invoice.setInvoiceNumber(generateInvoiceNumber(project));
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(summary.getEndDate() != null ? summary.getEndDate().plusDays(30) : LocalDate.now().plusDays(30));
        invoice.setAmount(summary.getTotalAmount() != null ? summary.getTotalAmount() : BigDecimal.ZERO);
        invoice.setCurrency(summary.getCurrency() != null ? summary.getCurrency() : "CHF");
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setProjectId(summary.getProjectId());
        CustomerInvoice saved = customerInvoiceRepository.save(invoice);
        postRevenueEntry(saved);
        return saved;
    }

    private String generateInvoiceNumber(Project project) {
        String prefix = project != null ? "PRJ" + project.getId() : "AUTO";
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void postRevenueEntry(CustomerInvoice invoice) {
        Account receivable = accountingService.ensureAccount("1100", "Forderungen aus Lieferungen", AccountType.ASSET);
        Account revenue = accountingService.ensureAccount("3200", "Dienstleistungserlöse", AccountType.REVENUE);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now());
        entry.setDescription("Invoice " + invoice.getInvoiceNumber());
        entry.setSource("AR");
        entry.setDocumentReference(invoice.getInvoiceNumber());

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(receivable);
        debit.setDebit(invoice.getAmount());
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo("Invoice " + invoice.getInvoiceNumber());

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(revenue);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(invoice.getAmount());
        credit.setMemo("Revenue " + invoice.getInvoiceNumber());

        entry.setLines(List.of(debit, credit));
        JournalEntry posted = accountingService.postEntry(entry);
        invoice.setJournalEntry(posted);
        customerInvoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Page<CustomerInvoice> findOpenInvoices(Pageable pageable) {
        return customerInvoiceRepository.findByStatusIn(
                Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID),
                pageable);
    }

    @Transactional
    public CustomerInvoice applyPayment(Long invoiceId, BigDecimal amount, LocalDate paymentDate, String memo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        Account bank = accountingService.ensureAccount("1000", "Bank", AccountType.ASSET);
        Account receivable = accountingService.ensureAccount("1100", "Forderungen aus Lieferungen", AccountType.ASSET);

        JournalEntry entry = new JournalEntry();
        LocalDate postingDate = paymentDate != null ? paymentDate : LocalDate.now();
        entry.setEntryDate(postingDate);
        entry.setDescription("Receipt " + invoice.getInvoiceNumber());
        entry.setSource("AR");
        entry.setDocumentReference(invoice.getInvoiceNumber());

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(bank);
        debit.setDebit(amount);
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo(memo != null && !memo.isBlank() ? memo : "Customer payment");

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(receivable);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(amount);
        credit.setMemo(memo != null && !memo.isBlank() ? memo : "Customer payment");

        entry.setLines(List.of(debit, credit));
        accountingService.postEntry(entry);

        BigDecimal newPaid = invoice.getAmountPaid().add(amount);
        if (newPaid.compareTo(invoice.getAmount()) >= 0) {
            invoice.setAmountPaid(invoice.getAmount());
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setAmountPaid(newPaid);
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoice.setLastPaymentDate(postingDate);
        return customerInvoiceRepository.save(invoice);
    }
}
