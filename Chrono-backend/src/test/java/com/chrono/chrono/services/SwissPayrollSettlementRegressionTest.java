package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.chrono.chrono.services.PayrollExtendedCalculationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SwissPayrollSettlementRegressionTest {
    private final SwissSourceTax source = new SwissSourceTax();

    @Test void finalAnnualTaxDoesNotAccumulateMonthlyRoundingResidue() {
        var t = PayrollTestTerms.change(terms("VD", "NONE"), Map.of("amount", "5001"));
        List<PayrollLedgerService.Entry> entries = new ArrayList<>();
        BigDecimal withheld = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            var result = month(t, month, gross("5001"), history(entries));
            withheld = withheld.add(withholding(result));
            entries.add(entry(month, t, gross("5001"), result));
        }
        // VD A0N 60,001–60,600: 8.34%. Round once: 60,012 * 8.34% = 5,005.0008.
        assertMoney("5005.00", withheld);
        assertEquals(417.10, entries.get(0).calculation().employee().get("Withholding tax"));
    }

    @Test void annualReconciliationKeepsHistoricalTariffsWhenRoundingTheirYearlyBases() {
        var a = PayrollTestTerms.change(terms("VD", "NONE"), Map.of("amount", "5001"));
        var b = PayrollTestTerms.change(a, Map.of("tariffCode", "B0N"));
        List<PayrollLedgerService.Entry> entries = new ArrayList<>();
        BigDecimal withheld = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            var t = month <= 6 ? a : b;
            var result = month(t, month, gross("5001"), history(entries));
            withheld = withheld.add(withholding(result));
            entries.add(entry(month, t, gross("5001"), result));
        }
        // Six months per tariff: 30,006 * 8.34% = 2,502.50; 30,006 * 2.35% = 705.15.
        assertMoney("3207.65", withheld);
        assertEquals("A0N", entries.get(0).calculation().terms().tariffCode());
        assertEquals(417.10, entries.get(0).calculation().employee().get("Withholding tax"));
    }

    @ParameterizedTest
    @CsvSource({"CALENDAR_DAYS,61428.56", "THIRTY_DAYS,60000"})
    void knownFutureExitUsesTheContractualPartialMonthSalary(String method, String expectedRateIncome) {
        var t = PayrollTestTerms.change(terms("VD", "NONE"), Map.of("contract", Map.of(
                "exitDate", "2026-02-15", "salaryProration", method,
                "pensionProration", "THIRTY_DAYS", "thirteenthFrequency", "NONE")));
        var result = month(t, 1, gross("5000"), empty());
        // Calendar contract: January 5,000 + February 5,000 * 15/28, rounded to 2,678.57;
        // statutory source-tax denominator remains 45 days in both cases.
        assertMoney(expectedRateIncome, result.getSwissBasis().annualRateIncome());
    }

    @Test void roundedThirteenthAtYearEndCannotMoveTheAnnualIncomeIntoTheNextTariffBand() {
        var t = PayrollTestTerms.change(terms("VD", "ANNUAL"), Map.of("amount", "4615"));
        List<PayrollLedgerService.Entry> entries = new ArrayList<>();
        BigDecimal withheld = BigDecimal.ZERO;
        for (int month = 1; month <= 11; month++) {
            var result = month(t, month, gross("4615"), history(entries));
            withheld = withheld.add(withholding(result));
            entries.add(entry(month, t, gross("4615"), result));
        }
        var decemberTerms = PayrollTestTerms.change(t, Map.of("amount", "4620.54"));
        var decemberGross = gross("4620.54").withThirteenth(n("4615.45"));
        var result = month(decemberTerms, 12, decemberGross, history(entries));
        // Base 55,385.54 + actually paid 13th 4,615.45 = 60,000.99, still VD A0N 8.17%.
        // The unrounded theoretical entitlement would produce 60,001.00166... and the wrong 8.34% band.
        assertMoney("60000.99", result.getSwissBasis().annualRateIncome());
        assertMoney("4902.10", withheld.add(withholding(result)));
    }

    @Test void exitUsesPreviouslyPaidAndCurrentRoundedThirteenthInsteadOfTheoreticalEntitlement() {
        var t = PayrollTestTerms.change(terms("VD", "MONTHLY"), Map.of("contract", Map.of(
                "exitDate", "2026-02-15", "salaryProration", "THIRTY_DAYS",
                "pensionProration", "THIRTY_DAYS", "thirteenthFrequency", "MONTHLY")));
        var janGross = gross("5000").withThirteenth(n("416.65"));
        var jan = month(t, 1, janGross, empty());
        var febGross = gross("2500.01").withThirteenth(n("208.35"));
        var result = SwissPayrollSettlement.calculate(source, coverage(), t, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 15), febGross, history(List.of(entry(1, t, janGross, jan))));
        // (7,500.01 + actually paid 625.00) / 45 * 360 = 65,000.08.
        assertMoney("65000.08", result.getSwissBasis().annualRateIncome());
    }

    @ParameterizedTest
    @CsvSource({"60000,120000", "120000,60000", "0,60000", "60000,0"})
    void datedKtgCapChangesCannotRetroactivelyRepricePastMonths(String oldCap, String newCap) {
        var firstCoverage = coverage(); firstCoverage.insurance().setKtgAnnualSalaryCap(n(oldCap));
        var t = PayrollTestTerms.change(terms("ZH", "NONE"), Map.of("amount", "10000"));
        var jan = SwissPayrollSettlement.calculate(source, firstCoverage, t, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), gross("10000"), empty());
        var nextCoverage = coverage(); nextCoverage.insurance().setId(13L);
        nextCoverage.insurance().setKtgAnnualSalaryCap(n(newCap));
        var error = assertThrows(IllegalStateException.class, () -> SwissPayrollSettlement.calculate(source,
                nextCoverage, t, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), gross("10000"),
                history(List.of(entry(1, t, gross("10000"), jan)))));
        assertTrue(error.getMessage().contains("KTG-Jahreslohngrenze ändert"));
        assertMoney(oldCap, jan.getSwissBasis().ktgAnnualSalaryCap());
    }

    @Test void rateOnlyInsuranceChangeKeepsTheConfirmedKtgCapAndAnnualHeadroom() {
        var firstCoverage = coverage(); firstCoverage.insurance().setKtgAnnualSalaryCap(n("60000"));
        var t = terms("ZH", "NONE");
        var jan = SwissPayrollSettlement.calculate(source, firstCoverage, t, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), gross("4000"), empty());
        var nextCoverage = coverage(); nextCoverage.insurance().setId(13L);
        nextCoverage.insurance().setKtgAnnualSalaryCap(n("60000.00"));
        nextCoverage.insurance().setKtgRateEmployee(n("0.01"));
        var feb = SwissPayrollSettlement.calculate(source, nextCoverage, t, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28), gross("7000"), history(List.of(entry(1, t, gross("4000"), jan))));
        assertMoney("6000", feb.getSwissBasis().ktgBase());
        assertEquals(60.0, feb.getEmployee().get("KTG"));
    }

    @Test void legacyBasisCanUseOnlyTheExactSameImmutableInsuranceVersion() {
        var t = terms("ZH", "NONE");
        var oldBasis = new SwissPayrollSettlement.Basis(30, n("5000"), n("5000"), null, n("5000"));
        var sameVersion = legacyEntry(t, oldBasis, "Versicherungsversion #11; BVG-Version #12; CH 2026; alter Tarifnachweis");
        assertDoesNotThrow(() -> month(t, 2, gross("5000"), history(List.of(sameVersion))));
        for (String reference : Arrays.asList(null, "Versicherungsversion #110; BVG-Version #12; CH 2026")) {
            var differentVersion = legacyEntry(t, oldBasis, reference);
            var error = assertThrows(IllegalStateException.class,
                    () -> month(t, 2, gross("5000"), history(List.of(differentVersion))));
            assertTrue(error.getMessage().contains("gesicherte KTG-Jahreslohngrenze"));
        }
    }

    @Test void oldSavedBasisWithoutKtgCapRemainsDeserializable() throws Exception {
        var basis = new ObjectMapper().readValue("{\"insuranceDays\":30,\"alvUvgBase\":5000,\"ktgBase\":5000,\"annualRateIncome\":60000,\"monthlyRateIncome\":null}", SwissPayrollSettlement.Basis.class);
        assertNull(basis.ktgAnnualSalaryCap());
        assertMoney("5000", basis.ktgBase());
    }

    private TaxCalculationService.Result month(PayrollTerms t, int month, PayrollGrossCalculator.Result gross,
            PayrollLedgerService.History history) {
        LocalDate start = LocalDate.of(2026, month, 1);
        return SwissPayrollSettlement.calculate(source, coverage(), t, start,
                start.withDayOfMonth(start.lengthOfMonth()), gross, history);
    }
    private static PayrollLedgerService.Entry legacyEntry(PayrollTerms terms, SwissPayrollSettlement.Basis basis, String reference) {
        var calculation = new PayrollCalculationService.Calculation(1L, terms, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), List.of(), gross("5000"), Map.of(), Map.of(), reference,
                basis, null, List.of(), null);
        return new PayrollLedgerService.Entry(new Payslip(), new Payslip(), calculation);
    }
    private static BigDecimal withholding(TaxCalculationService.Result result) {
        return BigDecimal.valueOf(result.getEmployee().getOrDefault("Withholding tax", 0.0));
    }
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
