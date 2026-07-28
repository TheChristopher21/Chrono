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
            throw new IllegalStateException("STRIPE_SECRET_KEY is required for PMS Stripe payments");
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
            throw new IllegalArgumentException("A Stripe PaymentIntent reference is required");
        }
        PaymentIntent intent = PaymentIntent.retrieve(providerReference.trim(), requestOptions);
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException("Stripe PaymentIntent is not captured");
        }
        if (!String.valueOf(property.getId()).equals(intent.getMetadata().get("chronoPropertyId"))
                || !String.valueOf(folio.getId()).equals(intent.getMetadata().get("chronoFolioId"))) {
            throw new IllegalStateException("Stripe PaymentIntent metadata does not match the folio");
        }
        if (!property.getCurrencyCode().equalsIgnoreCase(intent.getCurrency())) {
            throw new IllegalStateException("Stripe PaymentIntent currency does not match the property");
        }
        long expectedMinorUnits = toMinorUnits(amount, property.getCurrencyCode());
        if (intent.getAmountReceived() == null || intent.getAmountReceived() != expectedMinorUnits) {
            throw new IllegalStateException("Stripe PaymentIntent amount does not match the folio payment");
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
            throw new IllegalArgumentException("Unsupported currency");
        }
        return amount.setScale(digits, RoundingMode.UNNECESSARY)
                .movePointRight(digits)
                .longValueExact();
    }
}
