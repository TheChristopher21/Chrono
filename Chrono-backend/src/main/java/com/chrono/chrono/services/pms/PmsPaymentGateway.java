package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentMethod;

import java.math.BigDecimal;

public interface PmsPaymentGateway {

    boolean supports(PaymentMethod method);

    default String merchantContext(HotelProperty property) { return "SIMULATED"; }
    default void verifyMerchant(String context) throws Exception { }
    default String verifyCapturedPayment(HotelProperty property, Folio folio, BigDecimal amount, String reference, String context) throws Exception { return verifyCapturedPayment(property, folio, amount, reference); }
    default CheckoutResult createCheckout(HotelProperty property, Folio folio, BigDecimal amount, boolean authorization, String returnUrl, String requestId, String context) throws Exception { return createCheckout(property, folio, amount, authorization, returnUrl, requestId); }
    default CheckoutResult inspectCheckout(String sessionId, String context) throws Exception { return inspectCheckout(sessionId); }
    default void captureAuthorization(String id, BigDecimal amount, String currency, String requestId, String context) throws Exception { captureAuthorization(id, amount, currency, requestId); }
    default void releaseAuthorization(String sessionId, String intentId, String requestId, String context) throws Exception { releaseAuthorization(sessionId, intentId, requestId); }
    default RefundResult retrieveRefund(String id, String context) throws Exception { return retrieveRefund(id); }

    default void validateAmount(BigDecimal amount, String currencyCode) { PmsMoney.require(amount, currencyCode); }

    String verifyCapturedPayment(
            HotelProperty property,
            Folio folio,
            BigDecimal amount,
            String providerReference) throws Exception;

    record RefundResult(String providerId, String status, String paymentIntentId, BigDecimal amount, String currencyCode) {
        public RefundResult(String providerId,String status){this(providerId,status,null,null,null);}
    }
    record CheckoutResult(String sessionId, String checkoutUrl, String paymentIntentId, String status,
                          BigDecimal receivedAmount, BigDecimal capturableAmount, String currencyCode,
                          String propertyId, String folioId) {}

    default CheckoutResult createCheckout(HotelProperty property, Folio folio, BigDecimal amount, boolean authorization,
                                           String returnUrl, String requestId) throws Exception { throw new UnsupportedOperationException("Hosted Checkout nicht eingerichtet."); }
    default CheckoutResult inspectCheckout(String sessionId) throws Exception { throw new UnsupportedOperationException("Hosted Checkout nicht eingerichtet."); }
    default void captureAuthorization(String paymentIntentId, BigDecimal amount, String currencyCode, String requestId) throws Exception { throw new UnsupportedOperationException("Kartengarantie nicht eingerichtet."); }
    default void releaseAuthorization(String sessionId, String paymentIntentId, String requestId) throws Exception { throw new UnsupportedOperationException("Kartengarantie nicht eingerichtet."); }

    RefundResult refund(
            Payment original,
            BigDecimal amount,
            String reason,
            String idempotencyKey) throws Exception;

    default RefundResult retrieveRefund(String providerId) throws Exception {
        throw new UnsupportedOperationException("Der Anbieter unterstützt keine Statusabfrage.");
    }

    void voidPayment(
            Payment original,
            String reason,
            String idempotencyKey) throws Exception;
}
