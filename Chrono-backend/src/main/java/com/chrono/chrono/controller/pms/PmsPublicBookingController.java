package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.AvailabilityResponse;
import com.chrono.chrono.dto.pms.PmsExtensionsRequests;
import com.chrono.chrono.dto.pms.PublicBookingResponse;
import com.chrono.chrono.dto.pms.PublicBookingConfigurationResponse;
import com.chrono.chrono.services.pms.PmsExtensionsService;
import com.chrono.chrono.services.pms.PmsPublicRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/public/pms/booking")
public class PmsPublicBookingController {
    private final PmsExtensionsService service;
    private final PmsPublicRateLimiter rateLimiter;

    public PmsPublicBookingController(PmsExtensionsService service, PmsPublicRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{propertyCode}/availability")
    public ResponseEntity<AvailabilityResponse> availability(@PathVariable String propertyCode,
            @RequestParam LocalDate arrival, @RequestParam LocalDate departure, HttpServletRequest servletRequest) {
        PmsPublicRateLimiter.Decision decision = rateLimiter.check(servletRequest.getRemoteAddr(), "booking-read");
        if (!decision.allowed()) return limited(decision);
        return ResponseEntity.ok(service.publicAvailability(propertyCode, arrival, departure));
    }

    @GetMapping("/{propertyCode}")
    public ResponseEntity<PublicBookingConfigurationResponse> configuration(@PathVariable String propertyCode,
            HttpServletRequest servletRequest) {
        PmsPublicRateLimiter.Decision decision = rateLimiter.check(servletRequest.getRemoteAddr(), "booking-config");
        if (!decision.allowed()) return limited(decision);
        return ResponseEntity.ok(service.publicConfiguration(propertyCode));
    }

    @PostMapping("/{propertyCode}/reservations")
    public ResponseEntity<PublicBookingResponse> book(@PathVariable String propertyCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PmsExtensionsRequests.PublicBooking request, HttpServletRequest servletRequest) {
        PmsPublicRateLimiter.Decision decision = rateLimiter.check(servletRequest.getRemoteAddr(), "booking-create");
        if (!decision.allowed()) return limited(decision);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPublicBooking(propertyCode, idempotencyKey, request));
    }

    @PostMapping("/{propertyCode}/verify")
    public ResponseEntity<PublicBookingResponse> verify(@PathVariable String propertyCode,
            @Valid @RequestBody PmsExtensionsRequests.VerifyPublicBooking request,
            HttpServletRequest servletRequest) {
        PmsPublicRateLimiter.Decision decision = rateLimiter.check(servletRequest.getRemoteAddr(), "booking-verify");
        if (!decision.allowed()) return limited(decision);
        return ResponseEntity.ok(service.verifyPublicBooking(propertyCode, request.token()));
    }

    private <T> ResponseEntity<T> limited(PmsPublicRateLimiter.Decision decision) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds())).build();
    }
}
