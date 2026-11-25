package com.chrono.chrono.services.pms;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class PmsMoneyTest {
    @Test void usesIsoMinorUnitsWithoutSilentlyRoundingEnteredAmounts() {
        assertThat(PmsMoney.require(new BigDecimal("123"),"JPY")).hasScaleOf(0);
        assertThat(PmsMoney.require(new BigDecimal("1.234"),"KWD")).hasScaleOf(3);
        assertThat(PmsMoney.require(new BigDecimal("1.23"),"CHF")).hasScaleOf(2);
        assertThat(PmsMoney.require(new BigDecimal("1.2345"),"CLF")).hasScaleOf(4);
        assertThatThrownBy(()->PmsMoney.require(new BigDecimal("1.234"),"CHF")).hasMessageContaining("kleinsten Einheit");
        assertThatThrownBy(()->PmsMoney.require(new BigDecimal("123.5"),"JPY")).hasMessageContaining("kleinsten Einheit");
    }
    @Test void roundsComputedTaxesAtCurrencyPrecision() {
        assertThat(PmsMoney.round(new BigDecimal("123.5"),"JPY")).isEqualByComparingTo("124");
        assertThat(PmsMoney.round(new BigDecimal("1.2345"),"KWD")).isEqualByComparingTo("1.235");
        assertThat(PmsMoney.round(new BigDecimal("1.235"),"CHF")).isEqualByComparingTo("1.24");
    }
}
