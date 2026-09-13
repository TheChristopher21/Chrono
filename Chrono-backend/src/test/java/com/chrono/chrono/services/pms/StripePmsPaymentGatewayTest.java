package com.chrono.chrono.services.pms;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePmsPaymentGatewayTest {

    @Test
    void convertsAmountsUsingCurrencyExponent() {
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("120.45"), "CHF"))
                .isEqualTo(12045L);
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("120"), "JPY"))
                .isEqualTo(120L);
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("1.234"), "KWD"))
                .isEqualTo(1234L);
    }

    @Test
    void rejectsFractionalMinorUnits() {
        assertThatThrownBy(() -> StripePmsPaymentGateway.toMinorUnits(
                new BigDecimal("12.345"), "CHF"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test void usesStripeWireExceptionsWithoutChangingTheHotelAmount() {
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("5"), "ISK")).isEqualTo(500L);
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("5"), "UGX")).isEqualTo(500L);
        assertThat(StripePmsPaymentGateway.toMinorUnits(new BigDecimal("500"), "MGA")).isEqualTo(500L);
        assertThatThrownBy(() -> StripePmsPaymentGateway.toMinorUnits(new BigDecimal("5.1"), "ISK")).isInstanceOf(ArithmeticException.class);
    }
}
