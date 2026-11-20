package com.chrono.chrono.services.pms;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PmsBeds24BindingPolicyTest {
    @Test void exactOperatorTupleIsRequiredEvenWhenOneTokenCanReachManyHotels() {
        var policy = new PmsBeds24BindingPolicy("1|2|env:HOTEL_TOKEN|41;1|3|env:HOTEL_TOKEN|42");
        assertThatCode(() -> policy.require(1L, 2L, "env:HOTEL_TOKEN", 41L)).doesNotThrowAnyException();
        assertThatCode(() -> policy.require(1L, 3L, "env:HOTEL_TOKEN", 42L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.require(9L, 2L, "env:HOTEL_TOKEN", 41L)).isInstanceOf(PmsBeds24BindingPolicy.BindingDenied.class);
        assertThatThrownBy(() -> policy.require(1L, 3L, "env:HOTEL_TOKEN", 41L)).isInstanceOf(PmsBeds24BindingPolicy.BindingDenied.class);
        assertThatThrownBy(() -> policy.require(1L, 2L, "env:OTHER_TOKEN", 41L)).isInstanceOf(PmsBeds24BindingPolicy.BindingDenied.class);
        assertThatThrownBy(() -> policy.require(1L, 2L, "env:HOTEL_TOKEN", 42L)).isInstanceOf(PmsBeds24BindingPolicy.BindingDenied.class);
    }

    @Test void absentOrMalformedConfigurationDeniesOnlyWhenTheIntegrationIsUsed() {
        for (String config : new String[]{"", "1|2|env:HOTEL_TOKEN|41;", "*|2|env:HOTEL_TOKEN|41", "1|2|env:HOTEL_TOKEN|0", "1|2|env:HOTEL_TOKEN|9999999999999999999999"}) {
            var policy = new PmsBeds24BindingPolicy(config);
            assertThatThrownBy(() -> policy.require(1L, 2L, "env:HOTEL_TOKEN", 41L)).hasMessageContaining("APP_PMS_BEDS24_BINDINGS");
        }
    }
}
