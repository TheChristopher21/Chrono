package com.chrono.chrono.controller.accounting;

import com.chrono.chrono.dto.accounting.*;
import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.JournalEntryLine;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.AssetRepository;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import com.chrono.chrono.services.accounting.AccountsReceivableService;
import com.chrono.chrono.services.accounting.AssetManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

    private final AccountingService accountingService;
    private final AccountsReceivableService accountsReceivableService;
    private final AccountsPayableService accountsPayableService;
    private final AssetManagementService assetManagementService;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;

    public AccountingController(AccountingService accountingService,
                                AccountsReceivableService accountsReceivableService,
                                AccountsPayableService accountsPayableService,
                                AssetManagementService assetManagementService,
                                AccountRepository accountRepository,
                                AssetRepository assetRepository) {
        this.accountingService = accountingService;
        this.accountsReceivableService = accountsReceivableService;
        this.accountsPayableService = accountsPayableService;
        this.assetManagementService = assetManagementService;
        this.accountRepository = accountRepository;
        this.assetRepository = assetRepository;
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountDTO>> listAccounts() {
        List<AccountDTO> accounts = accountingService.listAccounts().stream()
                .map(AccountDTO::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountDTO> createAccount(@RequestBody CreateAccountRequest request) {
        Account saved = accountingService.saveAccount(request.toEntity());
        return ResponseEntity.created(URI.create("/api/accounting/accounts/" + saved.getId()))
                .body(AccountDTO.from(saved));
    }

    @GetMapping("/journal")
    public ResponseEntity<Page<JournalEntryDTO>> listJournal(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<JournalEntryDTO> entries = accountingService.listEntries(pageable)
                .map(JournalEntryDTO::from);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/assets")
    public ResponseEntity<List<AssetDTO>> listAssets() {
        List<AssetDTO> assets = assetManagementService.listAssets().stream()
                .map(AssetDTO::from)
                .toList();
        return ResponseEntity.ok(assets);
    }

    @PostMapping("/journal")
    public ResponseEntity<JournalEntryDTO> createJournalEntry(@RequestBody CreateJournalEntryRequest request) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(request.getEntryDate() != null ? request.getEntryDate() : LocalDate.now());
        entry.setDescription(request.getDescription());
        entry.setSource(request.getSource());
        entry.setDocumentReference(request.getDocumentReference());
        List<JournalEntryLine> lines = request.getLines() == null ? List.of() : request.getLines().stream()
                .map(line -> {
                    Account account = accountRepository.findById(line.getAccountId()).orElseThrow();
                    JournalEntryLine jel = new JournalEntryLine();
                    jel.setAccount(account);
                    jel.setDebit(BigDecimal.valueOf(line.getDebit()));
                    jel.setCredit(BigDecimal.valueOf(line.getCredit()));
                    jel.setMemo(line.getMemo());
                    return jel;
                }).toList();
        entry.setLines(lines);
        JournalEntry saved = accountingService.postEntry(entry);
        return ResponseEntity.created(URI.create("/api/accounting/journal/" + saved.getId()))
                .body(JournalEntryDTO.from(saved));
    }

    @GetMapping("/receivables/open")
    public ResponseEntity<Page<CustomerInvoiceDTO>> openReceivables(@RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<CustomerInvoiceDTO> invoices = accountsReceivableService.findOpenInvoices(pageable)
                .map(CustomerInvoiceDTO::from);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/payables/open")
    public ResponseEntity<Page<VendorInvoiceDTO>> openPayables(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<VendorInvoiceDTO> invoices = accountsPayableService.findPendingInvoices(pageable)
                .map(VendorInvoiceDTO::from);
        return ResponseEntity.ok(invoices);
    }

    @PostMapping("/assets")
    public ResponseEntity<AssetDTO> registerAsset(@RequestBody CreateAssetRequest request) {
        Asset saved = assetManagementService.registerAsset(request.toEntity());
        return ResponseEntity.created(URI.create("/api/accounting/assets/" + saved.getId()))
                .body(AssetDTO.from(saved));
    }

    @PostMapping("/assets/{id}/depreciate")
    public ResponseEntity<AssetDTO> depreciate(@PathVariable Long id) {
        return assetRepository.findById(id)
                .map(assetManagementService::runMonthlyDepreciation)
                .map(AssetDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/receivables/{id}/payments")
    public ResponseEntity<CustomerInvoiceDTO> applyReceivablePayment(@PathVariable Long id,
                                                                     @RequestBody RecordPaymentRequest request) {
        var updated = accountsReceivableService.applyPayment(id, request.getAmount(),
                request.getPaymentDate(), request.getMemo());
        return ResponseEntity.ok(CustomerInvoiceDTO.from(updated));
    }

    @PostMapping("/payables/{id}/payments")
    public ResponseEntity<VendorInvoiceDTO> applyPayablePayment(@PathVariable Long id,
                                                                @RequestBody RecordPaymentRequest request) {
        var updated = accountsPayableService.applyPayment(id, request.getAmount(),
                request.getPaymentDate(), request.getMemo());
        return ResponseEntity.ok(VendorInvoiceDTO.from(updated));
    }
}
