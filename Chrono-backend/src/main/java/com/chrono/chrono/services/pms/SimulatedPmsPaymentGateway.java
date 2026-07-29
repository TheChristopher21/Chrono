package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("!live")
@ConditionalOnProperty(name = "app.pms.payments.simulated.enabled", havingValue = "true")
public class SimulatedPmsPaymentGateway implements PmsPaymentGateway {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public String verifyCapturedPayment(
            HotelProperty property,
            Folio folio,
            BigDecimal amount,
            String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Für die Testzahlung ist eine Zahlungsreferenz erforderlich."
            );
        }
        return providerReference.trim();
    }

    @Override
    public void refund(Payment original, BigDecimal amount, String reason, String idempotencyKey) {
        // Local-only simulation. The live profile can never load this component.
    }

    @Override
    public void voidPayment(Payment original, String reason, String idempotencyKey) {
        // Local-only simulation. The live profile can never load this component.
    }
}
