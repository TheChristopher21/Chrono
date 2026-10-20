package com.chrono.chrono.services;

import com.chrono.chrono.entities.PayrollConfigurationVersion;
import com.chrono.chrono.entities.User;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independently specified contract and contribution examples; no undated fallback. */
class PayrollContractCalculationTest {
    final PayrollConfigurationService configuration = mock(PayrollConfigurationService.class);
    final PayrollTermsService terms = mock(PayrollTermsService.class);
    final TaxCalculationService service = new TaxCalculationService(configuration, terms, new com.chrono.chrono.services.payroll.SwissSourceTax());
    final LocalDate start = LocalDate.of(2026,1,1), end = LocalDate.of(2026,1,31);
    User user() { User user = new User(); user.setCountry("CH"); user.setDailyWorkHours(8.0); user.setExpectedWorkDays(5);
        when(terms.resolve(eq(user),any(),any())).thenReturn(new PayrollTermsService.Resolved(1L, PayrollTestTerms.swiss())); return user; }
    PayrollConfigurationService.Coverage coverage(boolean changed) {
        var insurance = new PayrollConfigurationVersion(); insurance.setId(11L);
        insurance.setUvgBuRate(new BigDecimal("0.0125")); insurance.setUvgNbuRate(BigDecimal.ZERO);
        insurance.setKtgRateEmployee(new BigDecimal("0.004")); insurance.setKtgRateEmployer(new BigDecimal("0.006")); insurance.setFakRate(new BigDecimal("0.02"));
        insurance.setKtgAnnualSalaryCap(BigDecimal.ZERO); insurance.setFakEmployeeRate(BigDecimal.ZERO); insurance.setAhvAdminRate(BigDecimal.ZERO);
        var pension = new PayrollConfigurationVersion(); pension.setId(12L);
        pension.setBvgEmployee(new BigDecimal("240.50")); pension.setBvgEmployer(new BigDecimal("300.75"));
        return new PayrollConfigurationService.Coverage(insurance,pension,changed,false);
    }
    @Test void appliesExactPersonalBvgAndEffectiveInsuranceIncludingExplicitZero() {
        User user = user(); when(configuration.resolve(user,start,end)).thenReturn(coverage(false));
        var result = service.calculate(user,5000,start,end);
        assertEquals(240.50,result.getEmployee().get("BVG")); assertEquals(300.75,result.getEmployer().get("BVG"));
        assertFalse(result.getEmployee().containsKey("NBU"));
        assertEquals(62.50,result.getEmployer().get("BU"));
        assertEquals(20.0,result.getEmployee().get("KTG")); assertEquals(30.0,result.getEmployer().get("KTG"));
        assertEquals(100.0,result.getEmployer().get("FAK"));
        assertTrue(result.getConfigurationReference().contains("Versicherungsversion #11; BVG-Version #12"));
        assertFalse(result.getEmployee().containsKey("Income tax"));
        assertEquals(265.0, result.getEmployee().get("AHV/IV/EO"));
        assertEquals(55.0, result.getEmployee().get("ALV"));
    }
    @Test void neverFallsBackToGuessedRatesWhenContractValuesAreMissing() {
        User user = user(); when(configuration.resolve(user,start,end)).thenReturn(new PayrollConfigurationService.Coverage(null,null,false,false));
        assertThrows(IllegalStateException.class, () -> service.calculate(user,5000,start,end));
    }
    @Test void rejectsChangesWithinThePayrollPeriod() {
        User user = user(); when(configuration.resolve(user,start,end)).thenReturn(coverage(true));
        assertThrows(IllegalStateException.class, () -> service.calculate(user,5000,start,end));
    }
    @Test void neverChargesAFullMonthlyBvgAmountForAPartialPeriod() {
        User user = user(); when(configuration.resolve(user,start.plusDays(1),end)).thenReturn(coverage(false));
        assertThrows(IllegalStateException.class, () -> service.calculate(user,5000,start.plusDays(1),end));
    }
    @Test void rejectsUnknownCountriesAndInvalidGrossInsteadOfGenericDeductions() {
        User user = new User();
        when(terms.resolve(user,start,end)).thenThrow(new IllegalStateException("Lohnregeln fehlen"));
        assertThrows(IllegalStateException.class, () -> service.calculate(user,5000,start,end));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(user,Double.NaN,start,end));
        verifyNoInteractions(configuration);
    }
    @Test void addsRoundedPositionsWithoutFloatingPointResidue() {
        var result = new TaxCalculationService.Result(Map.of("a",0.1,"b",0.2),Map.of());
        assertEquals(0.3,result.getEmployeeTotal());
    }
    @Test void highRegularSwissSalaryHasCappedAlvAndUvgButNoOrdinaryIncomeTaxDeduction() {
        User user=user(); when(configuration.resolve(user,start,end)).thenReturn(coverage(false));
        var rule=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("amount","30000"));
        var result=service.calculate(user,new BigDecimal("30000"),start,end,rule);
        assertEquals(1590.0,result.getEmployee().get("AHV/IV/EO"));
        assertEquals(135.85,result.getEmployee().get("ALV")); assertEquals(154.40,result.getEmployer().get("BU"));
        assertFalse(result.getEmployee().containsKey("Income tax"));assertFalse(result.getEmployee().containsKey("Withholding tax"));
    }
    @Test void appliesAhvContributionStartByBirthYearAndNbuAtEightHours() {
        User user=user(); var c=coverage(false); c.insurance().setUvgNbuRate(new BigDecimal("0.01"));
        when(configuration.resolve(user,start,end)).thenReturn(c);
        var rule=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("birthDate","2009-01-01","weeklyHours","7.5"));
        var result=service.calculate(user,new BigDecimal("5000"),start,end,rule);
        assertFalse(result.getEmployee().containsKey("AHV/IV/EO"));assertFalse(result.getEmployee().containsKey("ALV"));assertFalse(result.getEmployee().containsKey("NBU"));
        rule=PayrollTestTerms.change(rule,Map.of("birthDate","2008-12-31","weeklyHours","8"));
        result=service.calculate(user,new BigDecimal("5000"),start,end,rule);
        assertEquals(265.0,result.getEmployee().get("AHV/IV/EO"));assertEquals(50.0,result.getEmployee().get("NBU"));
    }
    @Test void sourceTaxUsesActualCantonalTariffAndNeverAddsOrdinaryIncomeTax() {
        User user=user(); when(configuration.resolve(user,start,end)).thenReturn(coverage(false));
        var rule=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("taxMode","CH_SOURCE","canton","ZH","tariffCode","A0N"));
        var result=service.calculate(user,new BigDecimal("5000"),start,end,rule);
        assertEquals(276.0,result.getEmployee().get("Withholding tax"));assertFalse(result.getEmployee().containsKey("Income tax"));
    }
    @Test void refusesVariableSalaryAboveAContractualKtgCapWithoutAnnualHistory() {
        User user=user(); var c=coverage(false); c.insurance().setKtgAnnualSalaryCap(new BigDecimal("66000"));
        when(configuration.resolve(user,start,end)).thenReturn(c);
        var error=assertThrows(IllegalStateException.class, () -> service.calculate(user,6000,start,end));
        assertTrue(error.getMessage().contains("KTG-Grenze"));
    }
}
