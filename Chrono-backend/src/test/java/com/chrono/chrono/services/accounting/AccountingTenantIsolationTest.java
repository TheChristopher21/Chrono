package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;
import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.JournalEntryLine;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.AssetRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({AccountingService.class, AssetManagementService.class})
@ActiveProfiles("test")
class AccountingTenantIsolationTest {
    @Autowired private AccountingService accounting;
    @Autowired private AssetManagementService assets;
    @Autowired private AccountRepository accounts;
    @Autowired private JournalEntryRepository journals;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TestEntityManager entityManager;

    private Company praxis;
    private Company other;

    @BeforeEach
    void companies() {
        praxis = entityManager.persistAndFlush(new Company("Praxis"));
        other = entityManager.persistAndFlush(new Company("Other"));
    }

    @Test
    void eachCompanyKeepsItsOwnChartAndMayReuseAnotherCompanyAccountCode() {
        List<Account> praxisChart = accounting.listAccounts(praxis);
        List<Account> otherChart = accounting.listAccounts(other);
        assertThat(praxisChart).extracting(Account::getCode)
                .containsExactlyElementsOf(otherChart.stream().map(Account::getCode).toList());
        assertThat(praxisChart).allSatisfy(account ->
                assertThat(account.getCompany().getId()).isEqualTo(praxis.getId()));
        assertThat(otherChart).allSatisfy(account ->
                assertThat(account.getCompany().getId()).isEqualTo(other.getId()));
        assertThat(praxisChart).extracting(Account::getId)
                .doesNotContainAnyElementsOf(otherChart.stream().map(Account::getId).toList());

        Account candidate = new Account("8890", "Praxis reserve", AccountType.LIABILITY);
        candidate.setCompany(other);
        candidate.setNotes("Praxis-only account notes");
        Account praxisAccount = accounting.saveAccount(praxis, candidate);
        Account otherAccount = accounting.saveAccount(other,
                new Account("8890", "Other reserve", AccountType.LIABILITY));
        entityManager.flush();
        entityManager.clear();

        assertThat(accounting.requireAccount(praxis, praxisAccount.getId()).getName()).isEqualTo("Praxis reserve");
        assertThat(accounting.requireAccount(other, otherAccount.getId()).getName()).isEqualTo("Other reserve");
        assertThat(accounting.listAccounts(praxis)).extracting(Account::getId).contains(praxisAccount.getId())
                .doesNotContain(otherAccount.getId());
        assertThat(accounting.listAccounts(other)).extracting(Account::getId).contains(otherAccount.getId())
                .doesNotContain(praxisAccount.getId());
        assertThat(accounting.ensureAccount(praxis, "8890", "Unchanged", AccountType.EQUITY).getId())
                .isEqualTo(praxisAccount.getId());
    }

    @Test
    void balancedJournalOwnershipComesFromTheCompanyAndListsNeverMixCompanies() {
        JournalEntry candidate = balancedEntry(praxis, "Praxis manual", "125.37");
        candidate.setCompany(other);
        JournalEntry praxisEntry = accounting.postEntry(praxis, candidate);
        JournalEntry otherEntry = accounting.postEntry(other, balancedEntry(other, "Other manual", "803.29"));
        entityManager.flush();
        entityManager.clear();

        var praxisEntries = accounting.listEntries(praxis, PageRequest.of(0, 20));
        var otherEntries = accounting.listEntries(other, PageRequest.of(0, 20));
        assertThat(praxisEntries.getContent()).extracting(JournalEntry::getId).containsExactly(praxisEntry.getId());
        assertThat(otherEntries.getContent()).extracting(JournalEntry::getId).containsExactly(otherEntry.getId());
        assertThat(praxisEntries.getContent().get(0).getCompany().getId()).isEqualTo(praxis.getId());
        assertThat(praxisEntries.getContent().get(0).getLines()).allSatisfy(line ->
                assertThat(line.getAccount().getCompany().getId()).isEqualTo(praxis.getId()));
        assertBalanced(praxisEntries.getContent().get(0), "125.37");
        assertBalanced(otherEntries.getContent().get(0), "803.29");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foreignAccountCannotBePostedEvenWhenItsDetachedOwnerIsSpoofed(boolean spoofOwner) {
        Account own = accounting.ensureAccount(praxis, "1000", "Praxis bank", AccountType.ASSET);
        Account foreign = accounting.ensureAccount(other, "5000", "Other expense", AccountType.EXPENSE);
        Long foreignId = foreign.getId();
        entityManager.flush();
        entityManager.clear();
        Account supplied = foreign;
        if (spoofOwner) {
            supplied = new Account("5000", "Forged Praxis expense", AccountType.EXPENSE);
            supplied.setId(foreignId);
            supplied.setCompany(praxis);
        }
        JournalEntry entry = entry("Rejected foreign account", own, supplied, "17.43");

        assertNotFound(() -> accounting.postEntry(praxis, entry));

        assertThat(journals.count()).isZero();
        assertThat(entry.getId()).isNull();
        assertThat(accounts.findById(foreignId).orElseThrow().getCompany().getId()).isEqualTo(other.getId());
    }

    @Test
    void foreignAccountCannotBecomeTheParentOfANewCompanyAccount() {
        Account foreign = accounting.ensureAccount(other, "1000", "Other bank", AccountType.ASSET);
        Account forgedParent = new Account();
        forgedParent.setId(foreign.getId());
        forgedParent.setCompany(praxis);
        Account child = new Account("1001", "Praxis child", AccountType.ASSET);
        child.setParent(forgedParent);

        assertNotFound(() -> accounting.saveAccount(praxis, child));

        assertThat(accounts.findByCompany_IdAndCode(praxis.getId(), "1001")).isEmpty();
        assertThat(accounts.findById(foreign.getId()).orElseThrow().getParent()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foreignAssetCannotBeListedOrDepreciatedEvenWhenItsDetachedOwnerIsSpoofed(boolean spoofOwner) {
        Asset own = assets.registerAsset(praxis, asset("Praxis scanner", "2400.00", 24));
        Asset foreign = assets.registerAsset(other, asset("Other scanner", "3600.00", 36));
        Long foreignId = foreign.getId();
        Long foreignAcquisition = foreign.getAcquisitionEntry().getId();
        entityManager.flush();
        entityManager.clear();
        Asset supplied = foreign;
        if (spoofOwner) {
            supplied = asset("Forged Praxis scanner", "1.00", 1);
            supplied.setId(foreignId);
            supplied.setCompany(praxis);
        }
        Asset candidate = supplied;

        assertThat(assets.listAssets(praxis)).extracting(Asset::getId).containsExactly(own.getId());
        assertNotFound(() -> assets.runMonthlyDepreciation(praxis, candidate));

        Asset preserved = assetRepository.findById(foreignId).orElseThrow();
        assertThat(preserved.getCompany().getId()).isEqualTo(other.getId());
        assertThat(preserved.getAssetName()).isEqualTo("Other scanner");
        assertThat(preserved.getAcquisitionCost()).isEqualByComparingTo("3600.00");
        assertThat(preserved.getAccumulatedDepreciation()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preserved.getLastDepreciationDate()).isNull();
        assertThat(preserved.getAcquisitionEntry().getId()).isEqualTo(foreignAcquisition);
        assertThat(journals.count()).isEqualTo(2);
    }

    @Test
    void ownAssetAcquisitionAndDepreciationStayWithinItsCompany() {
        Asset candidate = asset("Praxis scanner", "2400.00", 24);
        candidate.setCompany(other);
        Asset registered = assets.registerAsset(praxis, candidate);
        Asset depreciated = assets.runMonthlyDepreciation(praxis, registered);
        entityManager.flush();
        entityManager.clear();

        assertThat(depreciated.getCompany().getId()).isEqualTo(praxis.getId());
        assertThat(assetRepository.findById(registered.getId()).orElseThrow().getAccumulatedDepreciation())
                .isEqualByComparingTo("100.00");
        var entries = accounting.listEntries(praxis, PageRequest.of(0, 20)).getContent();
        assertThat(entries).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.getCompany().getId()).isEqualTo(praxis.getId());
            assertThat(entry.getLines()).allSatisfy(line ->
                    assertThat(line.getAccount().getCompany().getId()).isEqualTo(praxis.getId()));
        });
        assertThat(accounting.listEntries(other, PageRequest.of(0, 20))).isEmpty();
        assertThat(assets.listAssets(other)).isEmpty();
    }

    @Test
    void ownerlessLegacyAccountsJournalAndAssetArePreservedAndNeverBecomeTenantData() {
        Account legacyBank = entityManager.persist(new Account("1000", "Legacy bank", AccountType.ASSET));
        legacyBank.setNotes("Historic global notes");
        Account legacyExpense = entityManager.persist(new Account("5000", "Legacy expense", AccountType.EXPENSE));
        JournalEntry legacyJournal = entry("Historic acquisition", legacyBank, legacyExpense, "9000.12");
        legacyJournal.setSource("ASSET");
        legacyJournal.setDocumentReference("Historic free-form reference");
        legacyJournal.getLines().forEach(line -> line.setJournalEntry(legacyJournal));
        entityManager.persist(legacyJournal);
        Asset legacyAsset = asset("Historic asset", "9000.1234", 60);
        legacyAsset.setAccumulatedDepreciation(new BigDecimal("1234.5678"));
        legacyAsset.setLastDepreciationDate(LocalDate.of(2026, 10, 1));
        legacyAsset.setAcquisitionEntry(legacyJournal);
        entityManager.persistAndFlush(legacyAsset);
        Long bankId = legacyBank.getId();
        Long journalId = legacyJournal.getId();
        Long assetId = legacyAsset.getId();
        entityManager.clear();

        for (Company company : List.of(praxis, other)) {
            assertThat(accounting.listAccounts(company)).extracting(Account::getId).doesNotContain(bankId, legacyExpense.getId());
            assertThat(accounting.listEntries(company, PageRequest.of(0, 20))).isEmpty();
            assertThat(assets.listAssets(company)).isEmpty();
        }
        assertThat(accounting.hasUnassignedHistory()).isTrue();
        assertNotFound(() -> accounting.requireAccount(praxis, bankId));
        assertNotFound(() -> assets.runMonthlyDepreciation(praxis, legacyAsset));
        entityManager.flush();
        entityManager.clear();

        Account preservedBank = accounts.findById(bankId).orElseThrow();
        assertThat(preservedBank.getCompany()).isNull();
        assertThat(preservedBank.getName()).isEqualTo("Legacy bank");
        assertThat(preservedBank.getNotes()).isEqualTo("Historic global notes");
        JournalEntry preservedJournal = journals.findById(journalId).orElseThrow();
        assertThat(preservedJournal.getCompany()).isNull();
        assertThat(preservedJournal.getDocumentReference()).isEqualTo("Historic free-form reference");
        assertBalanced(preservedJournal, "9000.12");
        Asset preservedAsset = assetRepository.findById(assetId).orElseThrow();
        assertThat(preservedAsset.getCompany()).isNull();
        assertThat(preservedAsset.getAcquisitionCost()).isEqualByComparingTo("9000.1234");
        assertThat(preservedAsset.getAccumulatedDepreciation()).isEqualByComparingTo("1234.5678");
        assertThat(preservedAsset.getLastDepreciationDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(preservedAsset.getAcquisitionEntry().getId()).isEqualTo(journalId);
        assertThat(journals.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"8000.00,1200.00,6800.00,600.00", "-1845.63,-118.12,-1727.51,-177.18"})
    void signedPayrollAmountsAndOvertimeBalancesAreUnchangedAndPostingIsIdempotent(
            double gross, double deductions, double net, double employer) {
        Payslip payslip = persistedPayslip(gross, deductions, net, employer);
        Long payslipId = payslip.getId();
        Long userId = payslip.getUser().getId();
        JournalEntry first = accounting.recordPayrollPosting(payslip);
        JournalEntry replay = accounting.recordPayrollPosting(payslip);
        assertThat(replay.getId()).isEqualTo(first.getId());
        entityManager.flush();
        entityManager.clear();

        JournalEntry posted = journals.findById(first.getId()).orElseThrow();
        assertThat(posted.getCompany().getId()).isEqualTo(praxis.getId());
        assertThat(posted.getSource()).isEqualTo("PAYROLL");
        assertThat(posted.getDocumentReference()).isEqualTo("PAYSLIP-" + payslipId);
        assertThat(posted.getEntryDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(posted.getLines()).hasSize(5).allSatisfy(line ->
                assertThat(line.getAccount().getCompany().getId()).isEqualTo(praxis.getId()));
        assertSignedLine(posted, "5000", gross, true);
        assertSignedLine(posted, "5100", employer, true);
        assertSignedLine(posted, "1000", net, false);
        assertSignedLine(posted, "2000", deductions, false);
        assertSignedLine(posted, "2300", employer, false);
        assertBalanced(posted, BigDecimal.valueOf(gross).abs().add(BigDecimal.valueOf(employer).abs()).toPlainString());
        assertThat(accounting.listEntries(praxis, PageRequest.of(0, 20))).hasSize(1);
        assertThat(accounting.listEntries(other, PageRequest.of(0, 20))).isEmpty();
        assertPayslipUnchanged(entityManager.find(Payslip.class, payslipId), gross, deductions, net, employer);
        assertThat(entityManager.find(User.class, userId).getTrackingBalanceInMinutes()).isEqualTo(-385);
    }

    @Test
    void ambiguousLegacyPayrollReferenceNeitherMovesHistoryNorCreatesADuplicatePosting() {
        Payslip payslip = persistedPayslip(8000, 1200, 6800, 600);
        Account bank = entityManager.persist(new Account("1000", "Legacy bank", AccountType.ASSET));
        Account expense = entityManager.persist(new Account("5000", "Legacy wages", AccountType.EXPENSE));
        JournalEntry legacy = entry("Historic payroll", expense, bank, "8000.00");
        legacy.setSource("PAYROLL");
        legacy.setDocumentReference("PAYSLIP-" + payslip.getId());
        legacy.getLines().forEach(line -> line.setJournalEntry(legacy));
        entityManager.persistAndFlush(legacy);
        Long legacyId = legacy.getId();
        Long payslipId = payslip.getId();
        Long userId = payslip.getUser().getId();

        assertThatThrownBy(() -> accounting.recordPayrollPosting(payslip))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        entityManager.flush();
        entityManager.clear();

        assertThat(journals.count()).isEqualTo(1);
        assertThat(accounts.count()).isEqualTo(2);
        JournalEntry preserved = journals.findById(legacyId).orElseThrow();
        assertThat(preserved.getCompany()).isNull();
        assertThat(preserved.getDocumentReference()).isEqualTo("PAYSLIP-" + payslipId);
        assertBalanced(preserved, "8000.00");
        assertThat(accounting.listEntries(praxis, PageRequest.of(0, 20))).isEmpty();
        assertPayslipUnchanged(entityManager.find(Payslip.class, payslipId), 8000, 1200, 6800, 600);
        assertThat(entityManager.find(User.class, userId).getTrackingBalanceInMinutes()).isEqualTo(-385);
    }

    @Test
    void companyTransferCannotRepostAnExistingPayrollOrMoveItsHistory() {
        Payslip payslip = persistedPayslip(8000, 1200, 6800, 600);
        JournalEntry original = accounting.recordPayrollPosting(payslip);
        payslip.getUser().setCompany(other);
        entityManager.flush();

        assertThatThrownBy(() -> accounting.recordPayrollPosting(payslip))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(journals.count()).isEqualTo(1);
        assertThat(journals.findById(original.getId()).orElseThrow().getCompany().getId()).isEqualTo(praxis.getId());
        assertThat(accounting.listEntries(other, PageRequest.of(0, 20))).isEmpty();
        assertPayslipUnchanged(payslip, 8000, 1200, 6800, 600);
        assertThat(payslip.getUser().getTrackingBalanceInMinutes()).isEqualTo(-385);
    }

    private JournalEntry balancedEntry(Company company, String reference, String amount) {
        return entry(reference,
                accounting.ensureAccount(company, "1000", "Bank", AccountType.ASSET),
                accounting.ensureAccount(company, "5000", "Expense", AccountType.EXPENSE), amount);
    }

    private JournalEntry entry(String reference, Account debit, Account credit, String amount) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(LocalDate.of(2026, 10, 31));
        entry.setDescription(reference);
        entry.setDocumentReference(reference);
        entry.setSource("MANUAL");
        entry.setLines(List.of(line(debit, amount, "0.00"), line(credit, "0.00", amount)));
        return entry;
    }

    private JournalEntryLine line(Account account, String debit, String credit) {
        JournalEntryLine line = new JournalEntryLine();
        line.setAccount(account);
        line.setDebit(new BigDecimal(debit));
        line.setCredit(new BigDecimal(credit));
        line.setMemo("Synthetic isolation fixture");
        return line;
    }

    private Asset asset(String name, String cost, int months) {
        Asset asset = new Asset();
        asset.setAssetName(name);
        asset.setAcquisitionDate(LocalDate.of(2026, 1, 17));
        asset.setAcquisitionCost(new BigDecimal(cost));
        asset.setUsefulLifeMonths(months);
        return asset;
    }

    private Payslip persistedPayslip(double gross, double deductions, double net, double employer) {
        User user = new User();
        user.setUsername("synthetic.accounting.employee");
        user.setPassword("synthetic-test-password");
        user.setCountry("CH");
        user.setPersonnelNumber("SYNTHETIC-ACCOUNTING-1");
        user.setCompany(praxis);
        user.setTrackingBalanceInMinutes(-385);
        entityManager.persist(user);
        Payslip payslip = new Payslip();
        payslip.setUser(user);
        payslip.setGrossSalary(gross);
        payslip.setDeductions(deductions);
        payslip.setNetSalary(net);
        payslip.setEmployerContributions(employer);
        payslip.setOvertimeHours(12.75);
        payslip.setPayoutOvertime(true);
        payslip.setApproved(true);
        payslip.setLocked(true);
        payslip.setPeriodStart(LocalDate.of(2026, 10, 1));
        payslip.setPeriodEnd(LocalDate.of(2026, 10, 31));
        payslip.setPayoutDate(LocalDate.of(2026, 10, 31));
        return entityManager.persistAndFlush(payslip);
    }

    private void assertPayslipUnchanged(Payslip payslip, double gross, double deductions, double net, double employer) {
        assertThat(payslip.getGrossSalary()).isEqualTo(gross);
        assertThat(payslip.getDeductions()).isEqualTo(deductions);
        assertThat(payslip.getNetSalary()).isEqualTo(net);
        assertThat(payslip.getEmployerContributions()).isEqualTo(employer);
        assertThat(payslip.getOvertimeHours()).isEqualTo(12.75);
        assertThat(payslip.isPayoutOvertime()).isTrue();
        assertThat(payslip.isApproved()).isTrue();
        assertThat(payslip.isLocked()).isTrue();
        assertThat(payslip.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(payslip.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(payslip.getPayoutDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    private void assertSignedLine(JournalEntry entry, String code, double amount, boolean debitWhenPositive) {
        JournalEntryLine line = entry.getLines().stream()
                .filter(candidate -> code.equals(candidate.getAccount().getCode())).findFirst().orElseThrow();
        boolean debit = debitWhenPositive == (amount > 0);
        BigDecimal absolute = BigDecimal.valueOf(amount).abs();
        assertThat(line.getDebit()).isEqualByComparingTo(debit ? absolute : BigDecimal.ZERO);
        assertThat(line.getCredit()).isEqualByComparingTo(debit ? BigDecimal.ZERO : absolute);
    }

    private void assertBalanced(JournalEntry entry, String total) {
        assertThat(entry.getLines().stream().map(JournalEntryLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(total);
        assertThat(entry.getLines().stream().map(JournalEntryLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(total);
    }

    private void assertNotFound(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
