package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.JournalEntryLine;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import com.chrono.chrono.repositories.CompanyRepository;
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

    @Autowired
    private CompanyRepository companyRepository;

    private Company company;

    @BeforeEach
    void cleanEntries() {
        journalEntryRepository.deleteAll();
        company = companyRepository.save(new Company("Praxis"));
    }

    @Test
    void seedAccountsAreCreatedForTheRequestedCompany() {
        List<Account> accounts = accountingService.listAccounts(company);
        assertThat(accounts)
                .extracting(Account::getCode)
                .contains("1000", "1100", "2000", "3200", "5000");
    }

    @Test
    void postEntryRejectsUnbalancedJournal() {
        Account debitAccount = accountingService.ensureAccount(company, "9990", "Temp Debit", AccountType.ASSET);
        Account creditAccount = accountingService.ensureAccount(company, "9991", "Temp Credit", AccountType.LIABILITY);

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

        assertThatThrownBy(() -> accountingService.postEntry(company, entry))
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
        user.setCompany(company);
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

    @Test
    void recordPayrollPostingCreatesBalancedEntryForNegativePayslip() {
        Payslip payslip = new Payslip();
        payslip.setGrossSalary(-1845.63);
        payslip.setDeductions(-118.12);
        payslip.setNetSalary(-1727.51);
        payslip.setEmployerContributions(-177.18);
        payslip.setPayoutDate(LocalDate.of(2026, 3, 23));

        User user = new User();
        user.setUsername("mirjam.burkhardt");
        user.setCompany(company);
        user.setPassword("secret");
        user.setCountry("CH");
        user.setPersonnelNumber("EMP-2");
        payslip.setUser(user);

        JournalEntry entry = accountingService.recordPayrollPosting(payslip);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getLines()).hasSize(5);
        assertThat(entry.getLines()).allSatisfy(line -> {
            assertThat(line.getDebit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.getCredit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });

        BigDecimal totalDebit = entry.getLines().stream()
                .map(JournalEntryLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entry.getLines().stream()
                .map(JournalEntryLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    }

    @Test
    void legacyHalfCentRoundingCannotSilentlyCreateAnUnbalancedPosting() {
        Payslip payslip = legacyHalfCentPayslip();

        // The legacy generator subtracted doubles before rounding. Java rounds
        // gross to 18.78 but this stored net to 17.47: a one-cent imbalance.
        assertThatThrownBy(() -> accountingService.recordPayrollPosting(payslip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Journal entry not balanced");
        assertThat(journalEntryRepository.count()).isZero();
    }

    @Test
    void explicitlyCorrectedLegacyRoundingPostsWithoutChangingOvertimeOrApproving() {
        Payslip payslip = legacyHalfCentPayslip();
        payslip.setGrossSalary(18.78);
        payslip.setNetSalary(17.48);

        JournalEntry entry = accountingService.recordPayrollPosting(payslip);

        assertThat(entry.getLines().stream().map(JournalEntryLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("20.60");
        assertThat(entry.getLines().stream().map(JournalEntryLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("20.60");
        assertThat(entry.getLines()).filteredOn(line -> "Net payout".equals(line.getMemo()))
                .singleElement().satisfies(line -> assertThat(line.getCredit()).isEqualByComparingTo("17.48"));
        assertThat(payslip.getOvertimeHours()).isEqualTo(17.0 / 60);
        assertThat(payslip.isApproved()).isFalse();
    }

    private Payslip legacyHalfCentPayslip() {
        Payslip payslip = new Payslip();
        payslip.setId(94L);
        payslip.setGrossSalary(18.775);
        payslip.setDeductions(1.30);
        payslip.setNetSalary(17.474999999999998);
        payslip.setEmployerContributions(1.82);
        payslip.setOvertimeHours(17.0 / 60);
        payslip.setPayoutOvertime(true);
        User user = new User();
        user.setUsername("rounding.regression");
        user.setCompany(company);
        payslip.setUser(user);
        return payslip;
    }

    @Test
    void recordPayrollPostingIsIdempotentForTheSamePayslip() {
        Payslip payslip = new Payslip();
        payslip.setId(99L);
        payslip.setGrossSalary(100.0);
        payslip.setDeductions(10.0);
        payslip.setNetSalary(90.0);
        payslip.setEmployerContributions(5.0);
        payslip.setPayoutDate(LocalDate.of(2026, 8, 25));

        User user = new User();
        user.setUsername("hourly.employee");
        user.setCompany(company);
        payslip.setUser(user);

        JournalEntry first = accountingService.recordPayrollPosting(payslip);
        JournalEntry second = accountingService.recordPayrollPosting(payslip);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(journalEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void recordPayrollPostingSkipsZeroValuePayslip() {
        Payslip payslip = new Payslip();
        payslip.setId(100L);
        payslip.setGrossSalary(0.0);
        payslip.setDeductions(0.0);
        payslip.setNetSalary(0.0);
        payslip.setEmployerContributions(0.0);
        payslip.setUser(new User());

        JournalEntry entry = accountingService.recordPayrollPosting(payslip);

        assertThat(entry).isNull();
        assertThat(journalEntryRepository.count()).isZero();
    }
}
