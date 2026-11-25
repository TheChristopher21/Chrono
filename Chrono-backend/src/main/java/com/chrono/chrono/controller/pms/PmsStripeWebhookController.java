package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.PmsStripeWebhookService;
import org.springframework.web.bind.annotation.*;
@RestController
public class PmsStripeWebhookController {
    private final PmsStripeWebhookService service;
    public PmsStripeWebhookController(PmsStripeWebhookService service){this.service=service;}
    @PostMapping("/api/public/pms/webhooks/stripe")
    public void accept(@RequestBody String raw,@RequestHeader(value="Stripe-Signature",required=false) String signature){service.accept(raw,signature);}
}
