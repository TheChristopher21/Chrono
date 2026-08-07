package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.ExternalBookingRequest;
import com.chrono.chrono.dto.pms.ChannelWebhookResponse;
import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsPublicRateLimiter;
import com.chrono.chrono.services.pms.PmsWebhookSecurityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/api/public/pms/webhooks/channels")
public class PmsChannelWebhookController {
    private final PmsWebhookSecurityService securityService;
    private final PmsAdvancedService advancedService;
    private final PmsPublicRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PmsChannelWebhookController(PmsWebhookSecurityService securityService,
                                       PmsAdvancedService advancedService,
                                       PmsPublicRateLimiter rateLimiter,
                                       ObjectMapper objectMapper,
                                       Validator validator) {
        this.securityService = securityService;
        this.advancedService = advancedService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/{webhookKey}/bookings")
    public ResponseEntity<ChannelWebhookResponse> receiveBooking(
            @PathVariable String webhookKey,
            @RequestHeader("X-Chrono-Timestamp") String timestamp,
            @RequestHeader("X-Chrono-Delivery-Id") String deliveryId,
            @RequestHeader("X-Chrono-Signature") String signature,
            @RequestBody String rawBody,
            HttpServletRequest httpRequest) {
        PmsPublicRateLimiter.Decision limit =
                rateLimiter.check(httpRequest.getRemoteAddr(), "channel-webhook");
        if (!limit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(limit.retryAfterSeconds()))
                    .build();
        }
        ChannelConnection connection =
                securityService.verify(webhookKey, timestamp, deliveryId, signature, rawBody);
        ExternalBookingRequest request = parse(rawBody);
        if (!connection.getProviderCode().equalsIgnoreCase(request.channel())
                || !connection.getProperty().getId().equals(request.reservation().propertyId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Webhook-Provider und Hotelzuordnung stimmen nicht überein.");
        }
        return ResponseEntity.ok(advancedService.importExternalBookingForWebhook(
                connection.getProperty().getCompany(),
                request,
                "channel:" + connection.getProviderCode()));
    }

    private ExternalBookingRequest parse(String rawBody) {
        try {
            ExternalBookingRequest request =
                    objectMapper.readValue(rawBody, ExternalBookingRequest.class);
            Set<ConstraintViolation<ExternalBookingRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Webhook-Nutzdaten sind unvollständig.");
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Webhook-Nutzdaten sind kein gültiges JSON.");
        }
    }
}
