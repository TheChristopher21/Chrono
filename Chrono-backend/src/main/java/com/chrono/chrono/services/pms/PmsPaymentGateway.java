package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Payment;
import com.chrono.chrono.entities.pms.PaymentMethod;

import java.math.BigDecimal;

public interface PmsPaymentGateway {

    boolean supports(PaymentMethod method);

    String verifyCapturedPayment(
            HotelProperty property,
            Folio folio,
            BigDecimal amount,
            String providerReference) throws Exception;

    void refund(
            Payment original,
            BigDecimal amount,
            String reason,
            String idempotencyKey) throws Exception;

    void voidPayment(
            Payment original,
            String reason,
            String idempotencyKey) throws Exception;
}
