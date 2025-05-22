package com.chrono.chrono.jobs;

import com.chrono.chrono.services.TimeTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Ruft einmalig nach erfolgreichem Start des Back-Ends die
 * komplette Salden-Neuberechnung auf.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceRebuildStarter implements ApplicationListener<ApplicationReadyEvent> {

    private final TimeTrackingService timeTrackingService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("ApplicationReady – starte einmalige Balance-Neuberechnung …");
        timeTrackingService.rebuildAllUserBalancesOnce();
        log.info("Balance-Rebuild abgeschlossen ✅");
    }
}
