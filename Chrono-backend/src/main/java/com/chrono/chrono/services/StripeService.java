package com.chrono.chrono.services;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import com.stripe.param.PaymentIntentListParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StripeService {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public List<PaymentIntent> listLatestPayments(int limit) throws Exception {
        PaymentIntentListParams params = PaymentIntentListParams.builder()
                .setLimit(Long.valueOf(limit))
                .build();
        PaymentIntentCollection collection = PaymentIntent.list(params);
        return collection.getData();
    }

    /**
     * Listet Zahlungen, die in Stripe das Metadata-Feld
     * <code>companyId</code> auf den angegebenen Wert gesetzt haben.
     * Da die List-API nach Metadata nicht filtern kann, wird hier eine
     * kleine Client-Seite Filterung vorgenommen.
     */
    public List<PaymentIntent> listPaymentsForCompany(Long companyId) throws Exception {
        PaymentIntentListParams params = PaymentIntentListParams.builder()
                .setLimit(100L)
                .build();
        PaymentIntentCollection collection = PaymentIntent.list(params);
        String cid = String.valueOf(companyId);
        return collection.getData().stream()
                .filter(pi -> cid.equals(pi.getMetadata().get("companyId")))
                .toList();
    }
}
