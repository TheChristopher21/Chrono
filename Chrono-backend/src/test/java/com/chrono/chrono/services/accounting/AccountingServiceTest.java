package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.JournalEntryLine;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(AccountingService.class)
@ActiveProfiles("test")
class AccountingServiceTest {

    @Autowired
    private AccountingService accountingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @BeforeEach
    void cleanEntries() {
        journalEntryRepository.deleteAll();
    }

    @Test
    void ensureSeedAccountsAreCreatedOnStartup() {
        List<Account> accounts = accountRepository.findAll();
        assertThat(accounts)
                .extracting(Account::getCode)
                .contains("1000", "1100", "2000", "3200", "5000");
    }

    @Test
    void postEntryRejectsUnbalancedJournal() {
        Account debitAccount = accountingService.ensureAccount("9990", "Temp Debit", AccountType.ASSET);
        Account creditAccount = accountingService.ensureAccount("9991", "Temp Credit", AccountType.LIABILITY);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setDescription("Test");
        entry.setSource("MANUAL");

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(debitAccount);
        debit.setDebit(new BigDecimal("100.00"));
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo("Debit line");

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(creditAccount);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("90.00"));
        credit.setMemo("Credit line");

        entry.setLines(List.of(debit, credit));

        assertThatThrownBy(() -> accountingService.postEntry(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not balanced");
    }

    @Test
    void recordPayrollPostingCreatesBalancedEntry() {
        Payslip payslip = new Payslip();
        payslip.setGrossSalary(8000.0);
        payslip.setDeductions(1200.0);
        payslip.setNetSalary(6800.0);
        payslip.setEmployerContributions(600.0);
        payslip.setPayoutDate(LocalDate.of(2024, 12, 31));

        User user = new User();
        user.setUsername("jane.doe");
        user.setPassword("secret");
        user.setCountry("CH");
        user.setPersonnelNumber("EMP-1");
        payslip.setUser(user);

        JournalEntry entry = accountingService.recordPayrollPosting(payslip);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getLines()).hasSize(5);
        BigDecimal totalDebit = entry.getLines().stream()
                .map(JournalEntryLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entry.getLines().stream()
                .map(JournalEntryLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    }
}
