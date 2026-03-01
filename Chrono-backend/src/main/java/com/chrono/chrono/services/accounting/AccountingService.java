package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.accounting.*;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountingService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public AccountingService(AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    @PostConstruct
    public void ensureSeedAccounts() {
        ensureAccount("1000", "Bank", AccountType.ASSET);
        ensureAccount("1100", "Forderungen aus Lieferungen", AccountType.ASSET);
        ensureAccount("2000", "Verbindlichkeiten Personal", AccountType.LIABILITY);
        ensureAccount("2001", "Verbindlichkeiten Kreditoren", AccountType.LIABILITY);
        ensureAccount("2300", "Verbindlichkeiten Sozialversicherungen", AccountType.LIABILITY);
        ensureAccount("3200", "Dienstleistungserlöse", AccountType.REVENUE);
        ensureAccount("4000", "Materialaufwand", AccountType.EXPENSE);
        ensureAccount("5000", "Lohnaufwand", AccountType.EXPENSE);
        ensureAccount("5100", "Arbeitgeberbeiträge", AccountType.EXPENSE);
        ensureAccount("1500", "Anlagen im Bau", AccountType.ASSET);
        ensureAccount("1510", "Maschinen", AccountType.ASSET);
        ensureAccount("1520", "Anlagenabschreibung", AccountType.CONTRA_ASSET);
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
    public Account saveAccount(Account account) {
        if (account.getCode() != null) {
            accountRepository.findByCode(account.getCode())
                    .filter(existing -> !existing.getId().equals(account.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Account code already exists: " + account.getCode());
                    });
        }
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Page<JournalEntry> listEntries(Pageable pageable) {
        return journalEntryRepository.findAllByOrderByEntryDateDesc(pageable);
    }

    @Transactional
    public JournalEntry postEntry(JournalEntry entry) {
        if (entry.getEntryDate() == null) {
            entry.setEntryDate(LocalDate.now());
        }
        if (entry.getLines() == null || entry.getLines().isEmpty()) {
            throw new IllegalArgumentException("Journal entry requires lines");
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getAccount() == null) {
                throw new IllegalArgumentException("Journal line missing account");
            }
            if (line.getDebit() == null) {
                line.setDebit(BigDecimal.ZERO);
            }
            if (line.getCredit() == null) {
                line.setCredit(BigDecimal.ZERO);
            }
            if (line.getDebit().compareTo(BigDecimal.ZERO) < 0 || line.getCredit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Journal amounts must be non-negative");
            }
            if (line.getDebit().compareTo(BigDecimal.ZERO) == 0 && line.getCredit().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("Journal line requires debit or credit amount");
            }
            if (entry.getSource() == null || "MANUAL".equalsIgnoreCase(entry.getSource())) {
                if (line.getMemo() == null || line.getMemo().isBlank()) {
                    throw new IllegalArgumentException("Manual journal lines require a memo");
                }
            }
            debit = debit.add(line.getDebit());
            credit = credit.add(line.getCredit());
            line.setJournalEntry(entry);
        }
        if (debit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(credit.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Journal entry not balanced");
        }
        return journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalEntry recordPayrollPosting(Payslip payslip) {
        if (payslip == null) {
            return null;
        }
        Account wages = ensureAccount("5000", "Lohnaufwand", AccountType.EXPENSE);
        Account employer = ensureAccount("5100", "Arbeitgeberbeiträge", AccountType.EXPENSE);
        Account payrollLiability = ensureAccount("2000", "Verbindlichkeiten Personal", AccountType.LIABILITY);
        Account socialLiability = ensureAccount("2300", "Verbindlichkeiten Sozialversicherungen", AccountType.LIABILITY);
        Account bank = ensureAccount("1000", "Bank", AccountType.ASSET);

        BigDecimal gross = BigDecimal.valueOf(payslip.getGrossSalary()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal employerShare = BigDecimal.valueOf(payslip.getEmployerContributions())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal deductions = BigDecimal.valueOf(payslip.getDeductions()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = BigDecimal.valueOf(payslip.getNetSalary()).setScale(2, RoundingMode.HALF_UP);

        List<JournalEntryLine> lines = new ArrayList<>();
        lines.add(line(wages, gross, BigDecimal.ZERO, "Payroll " + payslip.getId()));
        if (employerShare.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(employer, employerShare, BigDecimal.ZERO, "Employer share"));
        }
        lines.add(line(bank, BigDecimal.ZERO, net, "Net payout"));
        if (deductions.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(payrollLiability, BigDecimal.ZERO, deductions, "Employee deductions"));
        }
        if (employerShare.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(socialLiability, BigDecimal.ZERO, employerShare, "Employer contributions"));
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(payslip.getPayoutDate() != null ? payslip.getPayoutDate() : LocalDate.now());
        entry.setDescription("Payroll for " + payslip.getUser().getUsername());
        entry.setSource("PAYROLL");
        entry.setDocumentReference("PAYSLIP-" + payslip.getId());
        entry.setLines(lines);
        return postEntry(entry);
    }

    private JournalEntryLine line(Account account, BigDecimal debit, BigDecimal credit, String memo) {
        JournalEntryLine line = new JournalEntryLine();
        line.setAccount(account);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setMemo(memo);
        return line;
    }

    public Account ensureAccount(String code, String name, AccountType type) {
        return accountRepository.findByCode(code)
                .orElseGet(() -> accountRepository.save(new Account(code, name, type)));
    }
}
