package com.chrono.chrono.services;

import com.chrono.chrono.services.payroll.SwissSourceTax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SwissSourceTaxTest {
    static final SwissSourceTax tax = new SwissSourceTax();
    @Test void keepsTheOfficial2025And2026ArchivesSeparate() {
        // Independent ZH 2025 PDF, p203421_QuellensteuerTarif_A_ohne_KS_ENT2.pdf: A0 5.75% and 6.49%.
        assertEquals(new BigDecimal("287.50"),tax.monthly(2025,"ZH","A0N",new BigDecimal("5000"),new BigDecimal("5000")).amount());
        assertEquals(new BigDecimal("356.95"),tax.monthly(2025,"ZH","A0N",new BigDecimal("5500"),new BigDecimal("5500")).amount());
        assertEquals(new BigDecimal("276.00"),tax.monthly(2026,"ZH","A0N",new BigDecimal("5000"),new BigDecimal("5000")).amount());
    }
    // Independently published ZH PDF 2026, tariff A without church tax, row 4,951–5,000: 5.52%.
    // https://www.zh.ch/content/dam/zhweb/bilder-dokumente/themen/steuern-finanzen/steuern/quellensteuer/quellensteuertarif/2026/p208840_QuellensteuerTarif_A_ohne_KS_ENT1.pdf
    @Test void matchesCantonalPdfAndKeepsCentsBelowNextTariffBoundary() {
        var result=tax.monthly(2026,"ZH","A0N",new BigDecimal("5000"),new BigDecimal("5000"));
        assertEquals(new BigDecimal("276.00"),result.amount());
        assertEquals(new BigDecimal("0.0552"),result.rate());
        result=tax.monthly(2026,"ZH","A0N",new BigDecimal("5000.99"),new BigDecimal("5000.99"));
        assertEquals(new BigDecimal("276.05"),result.amount()); assertEquals(new BigDecimal("0.0552"),result.rate());
        result=tax.monthly(2026,"ZH","A0N",new BigDecimal("5001"),new BigDecimal("5001"));
        assertEquals(new BigDecimal("279.55"),result.amount()); assertEquals(new BigDecimal("0.0559"),result.rate());
    }
    @ParameterizedTest @ValueSource(strings={"AG","AI","AR","BE","BL","BS","GL","GR","JU","LU","NE","NW","OW","SG","SH","SO","SZ","TG","UR","ZG","ZH"})
    void readsValidatedContiguousTariffsForAllMonthlyCantons(String canton) {
        // Jura publishes only the Y variant; absent variants must never be guessed.
        assertTrue(tax.monthly(2026,canton,canton.equals("JU") ? "A0Y" : "A0N",new BigDecimal("5000"),new BigDecimal("5000")).amount().signum()>=0);
        assertTrue(tax.monthly(2025,canton,canton.equals("JU") ? "A0Y" : "A0N",new BigDecimal("5000"),new BigDecimal("5000")).amount().signum()>=0);
    }
    @ParameterizedTest @ValueSource(strings={"FR","GE","TI","VD","VS"})
    void readsEvery2025AnnualCantonWithoutUsingThe2026Archive(String canton){assertTrue(tax.annual(2025,canton,"A0N",new BigDecimal("5000"),new BigDecimal("60000")).amount().signum()>=0);}
    @Test void doesNotInventTariffsOrApplyMonthlyRulesToAnnualCantons() {
        for (String canton : new String[]{"XX","FR","GE","TI","VD","VS"}) assertThrows(IllegalStateException.class,()->tax.monthly(2026,canton,"A0N",BigDecimal.TEN,BigDecimal.TEN));
        assertThrows(IllegalStateException.class,()->tax.monthly(2024,"ZH","A0N",BigDecimal.TEN,BigDecimal.TEN));
        assertThrows(IllegalStateException.class,()->tax.monthly(2026,"ZH","Q0N",BigDecimal.TEN,BigDecimal.TEN));
        assertThrows(IllegalStateException.class,()->tax.monthly(2026,"ZH","A0N",new BigDecimal("9999999"),new BigDecimal("9999999")));
    }
}
