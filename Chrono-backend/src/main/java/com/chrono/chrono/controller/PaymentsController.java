package com.chrono.chrono.controller;

import com.chrono.chrono.services.StripeService;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('SUPERADMIN')")
public class PaymentsController {

    @Autowired
    private StripeService stripeService;

    @GetMapping
    public ResponseEntity<?> listPayments() {
        try {
            List<PaymentIntent> payments = stripeService.listLatestPayments(20);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch payments");
        }
    }
}
