package com.chrono.chrono.jobs;

import com.chrono.chrono.services.pms.PmsOperationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PmsReservationHoldExpiryJob {
    private final PmsOperationsService operationsService;

    public PmsReservationHoldExpiryJob(PmsOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @Scheduled(
            fixedDelayString = "${pms.reservation-holds.expiry-interval:PT1M}",
            initialDelayString = "${pms.reservation-holds.initial-delay:PT1M}"
    )
    public void expireHolds() {
        operationsService.expireReservationHolds(LocalDateTime.now());
    }
}
