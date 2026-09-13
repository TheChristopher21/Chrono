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
 * Hosted Stripe adapter. Card entry stays on Stripe; only verified provider
 * status and tenant/folio metadata can produce a ledger payment.
 */
@Component
@ConditionalOnProperty(name = "app.pms.payments.stripe.enabled", havingValue = "true")
public class StripePmsPaymentGateway implements PmsPaymentGateway {

    private final RequestOptions requestOptions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.chrono.chrono.repositories.pms.PmsPaymentSettingsRepository settings;

    @Override public String merchantContext(HotelProperty property) {
        return settings.findById(property.getId()).map(com.chrono.chrono.entities.pms.PmsPaymentSettings::getMerchantContext)
                .filter(value -> value != null && !value.isBlank()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Für dieses Hotel zuerst ein verifiziertes Händlerkonto einrichten."));
    }

    RequestOptions options(String context) throws Exception {
        if (context == null || !context.matches("(PLATFORM|CONNECT):acct_[A-Za-z0-9]+"))
            throw new IllegalStateException("Der historische Händlerkontext fehlt. Zahlung vor weiteren Anbieteraktionen eindeutig zuordnen.");
        String account = context.substring(context.indexOf(':') + 1);
        if (context.startsWith("CONNECT:")) return requestOptions.toBuilder().setStripeAccount(account).build();
        if (!account.equals(com.stripe.model.Account.retrieve(requestOptions).getId()))
            throw new IllegalStateException("Der konfigurierte Schlüssel gehört nicht zum gespeicherten Händlerkonto.");
        return requestOptions;
    }

    @Override public void verifyMerchant(String context) throws Exception {
        RequestOptions scoped = options(context);
        com.stripe.model.Account account = com.stripe.model.Account.retrieve(scoped);
        if (!context.endsWith(":" + account.getId()) || !Boolean.TRUE.equals(account.getChargesEnabled()))
            throw new IllegalStateException("Das Händlerkonto ist nicht für Zahlungen freigeschaltet.");
    }

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

    @Override public void validateAmount(BigDecimal amount, String currencyCode) {
        PmsMoney.require(amount, currencyCode);
        try { toMinorUnits(amount, currencyCode); }
        catch (ArithmeticException error) { throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Der Kartenanbieter unterstützt diesen Teilbetrag in der Hotelwährung nicht."); }
    }

    @Override
    public CheckoutResult createCheckout(HotelProperty property, Folio folio, BigDecimal amount, boolean authorization,
                                           String returnUrl, String requestId) throws Exception {
        return createCheckout(property, folio, amount, authorization, returnUrl, requestId, merchantContext(property));
    }
    @Override public CheckoutResult createCheckout(HotelProperty property, Folio folio, BigDecimal amount, boolean authorization,
                                           String returnUrl, String requestId, String context) throws Exception {
        RequestOptions requestOptions = options(context);
        var metadata = java.util.Map.of("chronoPropertyId", property.getId().toString(), "chronoFolioId", folio.getId().toString(), "chronoRequestId", requestId);
        var params = new java.util.HashMap<String, Object>();
        params.put("mode", "payment"); params.put("payment_method_types", java.util.List.of("card"));
        params.put("success_url", returnUrl); params.put("cancel_url", returnUrl);
        params.put("client_reference_id", requestId); params.put("metadata", metadata);
        params.put("payment_intent_data", java.util.Map.of("metadata", metadata, "capture_method", authorization ? "manual" : "automatic"));
        params.put("line_items", java.util.List.of(java.util.Map.of("quantity", 1,
                "price_data", java.util.Map.of("currency", property.getCurrencyCode().toLowerCase(java.util.Locale.ROOT),
                        "unit_amount", toMinorUnits(amount, property.getCurrencyCode()),
                        "product_data", java.util.Map.of("name", authorization ? "Hotel · Kartengarantie" : "Hotel · Gastkontozahlung")))));
        var session = com.stripe.model.checkout.Session.create(params, requestOptions.toBuilder().setIdempotencyKey("chrono-checkout-" + requestId).build());
        return checkoutResult(session, requestOptions);
    }

    @Override public CheckoutResult inspectCheckout(String sessionId) throws Exception {
        throw new IllegalStateException("Gespeicherter Händlerkontext erforderlich.");
    }
    @Override public CheckoutResult inspectCheckout(String sessionId, String context) throws Exception {
        var scoped = options(context);
        return checkoutResult(com.stripe.model.checkout.Session.retrieve(sessionId, scoped), scoped);
    }

    private CheckoutResult checkoutResult(com.stripe.model.checkout.Session session, RequestOptions requestOptions) throws Exception {
        PaymentIntent intent = session.getPaymentIntent() == null ? null : PaymentIntent.retrieve(session.getPaymentIntent(), requestOptions);
        String currency = intent == null ? session.getCurrency() : intent.getCurrency();
        var metadata = intent == null ? session.getMetadata() : intent.getMetadata();
        String status = intent == null ? session.getStatus() : intent.getStatus();
        return new CheckoutResult(session.getId(), session.getUrl(), session.getPaymentIntent(), status,
                fromMinor(intent == null ? 0L : intent.getAmountReceived(), currency),
                fromMinor(intent == null ? 0L : intent.getAmountCapturable(), currency), currency,
                metadata.get("chronoPropertyId"), metadata.get("chronoFolioId"));
    }

    @Override public void captureAuthorization(String paymentIntentId, BigDecimal amount, String currencyCode, String requestId) throws Exception {
        throw new IllegalStateException("Gespeicherter Händlerkontext erforderlich.");
    }
    @Override public void captureAuthorization(String paymentIntentId, BigDecimal amount, String currencyCode, String requestId, String context) throws Exception {
        RequestOptions requestOptions = options(context);
        PaymentIntent.retrieve(paymentIntentId, requestOptions).capture(java.util.Map.of("amount_to_capture", toMinorUnits(amount, currencyCode)),
                requestOptions.toBuilder().setIdempotencyKey("chrono-capture-" + requestId).build());
    }

    @Override public void releaseAuthorization(String sessionId, String paymentIntentId, String requestId) throws Exception {
        throw new IllegalStateException("Gespeicherter Händlerkontext erforderlich.");
    }
    @Override public void releaseAuthorization(String sessionId, String paymentIntentId, String requestId, String context) throws Exception {
        RequestOptions requestOptions = options(context);
        var options = requestOptions.toBuilder().setIdempotencyKey("chrono-release-" + requestId).build();
        if (paymentIntentId == null) com.stripe.model.checkout.Session.retrieve(sessionId, requestOptions).expire(java.util.Map.of(), options);
        else PaymentIntent.retrieve(paymentIntentId, requestOptions).cancel(java.util.Map.of(), options);
    }

    private BigDecimal fromMinor(Long minor, String currencyCode) {
        if (minor == null || currencyCode == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(minor, providerDigits(currencyCode));
    }

    @Override
    public String verifyCapturedPayment(
            HotelProperty property,
            Folio folio,
            BigDecimal amount,
            String providerReference) throws Exception {
        return verifyCapturedPayment(property, folio, amount, providerReference, merchantContext(property));
    }
    @Override public String verifyCapturedPayment(HotelProperty property, Folio folio, BigDecimal amount, String providerReference, String context) throws Exception {
        RequestOptions requestOptions = options(context);
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
    public RefundResult refund(Payment original, BigDecimal amount, String reason, String idempotencyKey) throws Exception {
        RequestOptions requestOptions = options(original.getMerchantContext());
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(original.getReference())
                .setAmount(toMinorUnits(
                        amount,
                        original.getFolio().getReservation().getProperty().getCurrencyCode()))
                .putMetadata("chronoPaymentId", String.valueOf(original.getId()))
                .putMetadata("chronoRefundRequest", idempotencyKey)
                .putMetadata("reason", reason == null ? "" : reason)
                .build();
        Refund result = Refund.create(params, requestOptions.toBuilder().setIdempotencyKey(idempotencyKey).build());
        return new RefundResult(result.getId(), result.getStatus(),result.getPaymentIntent(),fromMinor(result.getAmount(),result.getCurrency()),result.getCurrency());
    }

    @Override
    public RefundResult retrieveRefund(String providerId) throws Exception {
        throw new IllegalStateException("Gespeicherter Händlerkontext erforderlich.");
    }
    @Override public RefundResult retrieveRefund(String providerId, String context) throws Exception {
        RequestOptions requestOptions = options(context);
        Refund result = Refund.retrieve(providerId, requestOptions);
        return new RefundResult(result.getId(), result.getStatus(),result.getPaymentIntent(),fromMinor(result.getAmount(),result.getCurrency()),result.getCurrency());
    }

    @Override
    public void voidPayment(Payment original, String reason, String idempotencyKey) throws Exception {
        refund(original, original.getAmount(), reason, idempotencyKey);
    }

    static long toMinorUnits(BigDecimal amount, String currencyCode) {
        String code = currencyCode.toUpperCase(java.util.Locale.ROOT);
        int digits = providerDigits(code);
        if (ZERO_DECIMAL.contains(code) || "ISK".equals(code) || "UGX".equals(code)) amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        return amount.setScale(digits, RoundingMode.UNNECESSARY)
                .movePointRight(digits)
                .longValueExact();
    }

    // Stripe retains two-decimal wire amounts for integer ISK/UGX, and treats MGA as zero-decimal.
    // https://docs.stripe.com/currencies#special-cases
    private static final java.util.Set<String> ZERO_DECIMAL = java.util.Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "VND", "VUV", "XAF", "XOF", "XPF");
    private static int providerDigits(String currencyCode) {
        String code = currencyCode.toUpperCase(java.util.Locale.ROOT);
        if ("ISK".equals(code) || "UGX".equals(code)) return 2;
        if (ZERO_DECIMAL.contains(code)) return 0;
        return PmsMoney.digits(code);
    }
}
