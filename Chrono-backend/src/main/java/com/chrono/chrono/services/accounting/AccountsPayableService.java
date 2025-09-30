package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.accounting.*;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AccountsPayableService {

    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final AccountingService accountingService;

    public AccountsPayableService(VendorInvoiceRepository vendorInvoiceRepository,
                                  AccountingService accountingService) {
        this.vendorInvoiceRepository = vendorInvoiceRepository;
        this.accountingService = accountingService;
    }

    @Transactional
    public VendorInvoice recordVendorInvoice(VendorInvoice invoice) {
        if (invoice.getInvoiceNumber() == null) {
            invoice.setInvoiceNumber("SUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(LocalDate.now());
        }
        if (invoice.getDueDate() == null) {
            invoice.setDueDate(invoice.getInvoiceDate().plusDays(30));
        }
        VendorInvoice saved = vendorInvoiceRepository.save(invoice);
        postExpenseEntry(saved);
        return saved;
    }

    private void postExpenseEntry(VendorInvoice invoice) {
        Account expense = accountingService.ensureAccount("4000", "Materialaufwand", AccountType.EXPENSE);
        Account liability = accountingService.ensureAccount("2001", "Verbindlichkeiten Kreditoren", AccountType.LIABILITY);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now());
        entry.setDescription("Vendor invoice " + invoice.getInvoiceNumber());
        entry.setSource("AP");
        entry.setDocumentReference(invoice.getInvoiceNumber());

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(expense);
        debit.setDebit(invoice.getAmount());
        debit.setCredit(java.math.BigDecimal.ZERO);
        debit.setMemo(invoice.getVendorName());

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(liability);
        credit.setDebit(java.math.BigDecimal.ZERO);
        credit.setCredit(invoice.getAmount());
        credit.setMemo(invoice.getVendorName());

        entry.setLines(List.of(debit, credit));
        JournalEntry posted = accountingService.postEntry(entry);
        invoice.setJournalEntry(posted);
        vendorInvoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Page<VendorInvoice> findPendingInvoices(Pageable pageable) {
        return vendorInvoiceRepository.findByStatus(InvoiceStatus.OPEN, pageable);
    }
}
