package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.CompleteGuestRegistrationRequest;
import com.chrono.chrono.dto.pms.PublicGuestRegistrationResponse;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsPublicRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/pms/guest-registration")
public class PmsPublicGuestRegistrationController {
    private final PmsAdvancedService advancedService;
    private final PmsPublicRateLimiter rateLimiter;

    public PmsPublicGuestRegistrationController(PmsAdvancedService advancedService,
                                                PmsPublicRateLimiter rateLimiter) {
        this.advancedService = advancedService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicGuestRegistrationResponse> getRegistration(
            @PathVariable String token,
            HttpServletRequest httpRequest) {
        PmsPublicRateLimiter.Decision limit = rateLimiter.check(httpRequest.getRemoteAddr(), "read");
        if (!limit.allowed()) {
            return rateLimited(limit);
        }
        return ResponseEntity.ok(advancedService.getPublicGuestRegistration(token));
    }

    @PostMapping("/{token}")
    public ResponseEntity<PublicGuestRegistrationResponse> completeRegistration(
            @PathVariable String token,
            @Valid @RequestBody CompleteGuestRegistrationRequest request,
            HttpServletRequest httpRequest) {
        PmsPublicRateLimiter.Decision limit = rateLimiter.check(httpRequest.getRemoteAddr(), "complete");
        if (!limit.allowed()) {
            return rateLimited(limit);
        }
        return ResponseEntity.ok(advancedService.completePublicGuestRegistration(token, request));
    }

    private ResponseEntity<PublicGuestRegistrationResponse> rateLimited(
            PmsPublicRateLimiter.Decision decision) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()))
                .build();
    }
}
