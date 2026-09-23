package com.chrono.chrono.dto.pms;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PmsPaymentRequestView(Long id, String requestId, Long folioId, String kind, String status, BigDecimal amount,
                                    BigDecimal captureAmount, String currencyCode, String checkoutUrl, String providerSessionId,
                                    String providerPaymentIntentId, String providerStatus, LocalDateTime createdAt, LocalDateTime updatedAt) {}
