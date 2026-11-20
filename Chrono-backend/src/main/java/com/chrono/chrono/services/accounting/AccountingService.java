package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.accounting.*;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
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

    @PersistenceContext
    private EntityManager entityManager;

    public AccountingService(AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    @Transactional
    public void ensureSeedAccounts(Company company) {
        ensureAccount(company, "1000", "Bank", AccountType.ASSET);
        ensureAccount(company, "1100", "Forderungen aus Lieferungen", AccountType.ASSET);
        ensureAccount(company, "2000", "Verbindlichkeiten Personal", AccountType.LIABILITY);
        ensureAccount(company, "2001", "Verbindlichkeiten Kreditoren", AccountType.LIABILITY);
        ensureAccount(company, "2300", "Verbindlichkeiten Sozialversicherungen", AccountType.LIABILITY);
        ensureAccount(company, "3200", "Dienstleistungserlöse", AccountType.REVENUE);
        ensureAccount(company, "4000", "Materialaufwand", AccountType.EXPENSE);
        ensureAccount(company, "5000", "Lohnaufwand", AccountType.EXPENSE);
        ensureAccount(company, "5100", "Arbeitgeberbeiträge", AccountType.EXPENSE);
        ensureAccount(company, "1500", "Anlagen im Bau", AccountType.ASSET);
        ensureAccount(company, "1510", "Maschinen", AccountType.ASSET);
        ensureAccount(company, "1520", "Anlagenabschreibung", AccountType.CONTRA_ASSET);
    }

    @Transactional
    public List<Account> listAccounts(Company company) {
        requireCompany(company);
        ensureSeedAccounts(company);
        return accountRepository.findByCompany_IdAndActiveTrueOrderByCodeAsc(company.getId());
    }

    @Transactional
    public Account saveAccount(Company company, Account account) {
        requireCompany(company);
        if (account.getId() != null) throw new IllegalArgumentException("New account must not have an ID");
        account.setCompany(company);
        if (account.getParent() != null) account.setParent(requireAccount(company, account.getParent().getId()));
        if (account.getCode() != null) {
            accountRepository.findByCompany_IdAndCode(company.getId(), account.getCode())
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Account code already exists: " + account.getCode());
                    });
        }
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Page<JournalEntry> listEntries(Company company, Pageable pageable) {
        requireCompany(company);
        return journalEntryRepository.findByCompany_IdOrderByEntryDateDesc(company.getId(), pageable);
    }

    @Transactional
    public JournalEntry postEntry(Company company, JournalEntry entry) {
        requireCompany(company);
        if (entry.getId() != null) throw new IllegalArgumentException("A posted journal entry cannot be overwritten");
        entry.setCompany(company);
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
            line.setAccount(requireAccount(company, line.getAccount().getId()));
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
        if (payslip.getGrossSalary() == 0 && payslip.getDeductions() == 0
                && payslip.getNetSalary() == 0 && payslip.getEmployerContributions() == 0) return null;
        Company company = payslip.getUser() == null ? null : payslip.getUser().getCompany();
        requireCompany(company);
        String documentReference = payslip.getId() != null ? "PAYSLIP-" + payslip.getId() : null;
        if (documentReference != null) {
            JournalEntry existingEntry = journalEntryRepository
                    .findFirstByCompany_IdAndSourceAndDocumentReferenceOrderByIdAsc(company.getId(), "PAYROLL", documentReference)
                    .orElse(null);
            if (existingEntry != null) {
                return existingEntry;
            }
            // A legacy text reference is not ownership evidence. Do not move it,
            // and do not silently create a second posting for the same payslip.
            if (journalEntryRepository.existsByCompanyIsNullAndSourceAndDocumentReference("PAYROLL", documentReference)
                    || journalEntryRepository.existsByCompany_IdNotAndSourceAndDocumentReference(company.getId(), "PAYROLL", documentReference)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Die bestehende Lohnbuchung muss vor einer erneuten Buchung vom Betreiber eindeutig zugeordnet werden.");
            }
        }
        Account wages = ensureAccount(company, "5000", "Lohnaufwand", AccountType.EXPENSE);
        Account employer = ensureAccount(company, "5100", "Arbeitgeberbeiträge", AccountType.EXPENSE);
        Account payrollLiability = ensureAccount(company, "2000", "Verbindlichkeiten Personal", AccountType.LIABILITY);
        Account socialLiability = ensureAccount(company, "2300", "Verbindlichkeiten Sozialversicherungen", AccountType.LIABILITY);
        Account bank = ensureAccount(company, "1000", "Bank", AccountType.ASSET);

        BigDecimal gross = BigDecimal.valueOf(payslip.getGrossSalary()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal employerShare = BigDecimal.valueOf(payslip.getEmployerContributions())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal deductions = BigDecimal.valueOf(payslip.getDeductions()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = BigDecimal.valueOf(payslip.getNetSalary()).setScale(2, RoundingMode.HALF_UP);

        List<JournalEntryLine> lines = new ArrayList<>();
        addSignedLine(lines, wages, gross, true, "Payroll " + payslip.getId());
        addSignedLine(lines, employer, employerShare, true, "Employer share");
        addSignedLine(lines, bank, net, false, "Net payout");
        addSignedLine(lines, payrollLiability, deductions, false, "Employee deductions");
        addSignedLine(lines, socialLiability, employerShare, false, "Employer contributions");

        if (lines.isEmpty()) {
            return null;
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(payslip.getPayoutDate() != null ? payslip.getPayoutDate() : LocalDate.now());
        entry.setDescription("Payroll for " + payslip.getUser().getUsername());
        entry.setSource("PAYROLL");
        entry.setDocumentReference(documentReference);
        entry.setLines(lines);
        return postEntry(company, entry);
    }

    private JournalEntryLine line(Account account, BigDecimal debit, BigDecimal credit, String memo) {
        JournalEntryLine line = new JournalEntryLine();
        line.setAccount(account);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setMemo(memo);
        return line;
    }

    private void addSignedLine(List<JournalEntryLine> lines, Account account, BigDecimal amount,
                               boolean debitWhenPositive, String memo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal absoluteAmount = amount.abs();
        boolean useDebit = debitWhenPositive ? amount.signum() > 0 : amount.signum() < 0;
        lines.add(line(
                account,
                useDebit ? absoluteAmount : BigDecimal.ZERO,
                useDebit ? BigDecimal.ZERO : absoluteAmount,
                memo
        ));
    }

    @Transactional
    public Account ensureAccount(Company company, String code, String name, AccountType type) {
        requireCompany(company);
        // Serializes first-use chart creation for one company, without touching
        // global legacy accounts or another company's chart.
        entityManager.find(Company.class, company.getId(), LockModeType.PESSIMISTIC_WRITE);
        return accountRepository.findByCompany_IdAndCode(company.getId(), code)
                .orElseGet(() -> {
                    Account account = new Account(code, name, type);
                    account.setCompany(company);
                    return accountRepository.save(account);
                });
    }

    public Account requireAccount(Company company, Long id) {
        requireCompany(company);
        return accountRepository.findByIdAndCompany_Id(id, company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public boolean hasUnassignedHistory() { return journalEntryRepository.existsByCompanyIsNull(); }

    static void requireCompany(Company company) {
        if (company == null || company.getId() == null) throw new AccessDeniedException("Company scope required for accounting");
    }
}
