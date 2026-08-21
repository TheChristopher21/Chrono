package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentMethod;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * PCI-minimizing Stripe adapter. Chrono accepts only an existing PaymentIntent
 * id and never card numbers or client secrets. The intent must already be
 * captured and carry tenant/folio metadata before it is posted to a folio.
 */
@Component
@ConditionalOnProperty(name = "app.pms.payments.stripe.enabled", havingValue = "true")
public class StripePmsPaymentGateway implements PmsPaymentGateway {

    private final RequestOptions requestOptions;

    public StripePmsPaymentGateway(@Value("${stripe.secret-key}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Kartenzahlungen sind noch nicht vollständig auf dem Server eingerichtet."
            );
        }
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey.trim()).build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public String verifyCapturedPayment(
            HotelProperty property,
            Folio folio,
            BigDecimal amount,
            String providerReference) throws Exception {
        if (providerReference == null || !providerReference.trim().startsWith("pi_")) {
            throw new IllegalArgumentException(
                    "Für die Kartenzahlung ist eine gültige Zahlungsreferenz erforderlich."
            );
        }
        PaymentIntent intent = PaymentIntent.retrieve(providerReference.trim(), requestOptions);
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException(
                    "Die Kartenzahlung wurde beim Zahlungsanbieter noch nicht erfolgreich abgeschlossen."
            );
        }
        if (!String.valueOf(property.getId()).equals(intent.getMetadata().get("chronoPropertyId"))
                || !String.valueOf(folio.getId()).equals(intent.getMetadata().get("chronoFolioId"))) {
            throw new IllegalStateException("Die Zahlungsreferenz gehört nicht zu diesem Gastkonto.");
        }
        if (!property.getCurrencyCode().equalsIgnoreCase(intent.getCurrency())) {
            throw new IllegalStateException(
                    "Die Währung der Kartenzahlung stimmt nicht mit dem Hotelbetrieb überein."
            );
        }
        long expectedMinorUnits = toMinorUnits(amount, property.getCurrencyCode());
        if (intent.getAmountReceived() == null || intent.getAmountReceived() != expectedMinorUnits) {
            throw new IllegalStateException(
                    "Der Betrag der Kartenzahlung stimmt nicht mit der Gastkonto-Zahlung überein."
            );
        }
        return intent.getId();
    }

    @Override
    public void refund(Payment original, BigDecimal amount, String reason, String idempotencyKey) throws Exception {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(original.getReference())
                .setAmount(toMinorUnits(
                        amount,
                        original.getFolio().getReservation().getProperty().getCurrencyCode()))
                .putMetadata("chronoPaymentId", String.valueOf(original.getId()))
                .putMetadata("reason", reason == null ? "" : reason)
                .build();
        Refund.create(params, requestOptions.toBuilder().setIdempotencyKey(idempotencyKey).build());
    }

    @Override
    public void voidPayment(Payment original, String reason, String idempotencyKey) throws Exception {
        refund(original, original.getAmount(), reason, idempotencyKey);
    }

    static long toMinorUnits(BigDecimal amount, String currencyCode) {
        int digits = Currency.getInstance(currencyCode.toUpperCase()).getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException("Die Währung wird für Kartenzahlungen nicht unterstützt.");
        }
        return amount.setScale(digits, RoundingMode.UNNECESSARY)
                .movePointRight(digits)
                .longValueExact();
    }
}
