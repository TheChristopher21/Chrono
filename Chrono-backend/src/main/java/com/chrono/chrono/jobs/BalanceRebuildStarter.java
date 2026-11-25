package com.chrono.chrono.jobs;

import com.chrono.chrono.services.TimeTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Startet eine komplette Salden-Neuberechnung nur, wenn sie explizit
 * per Konfiguration aktiviert wurde.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceRebuildStarter implements ApplicationListener<ApplicationReadyEvent> {

    private final TimeTrackingService timeTrackingService;

    @Value("${app.balance-rebuild-on-startup:false}")
    private boolean balanceRebuildOnStartup;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!balanceRebuildOnStartup) {
            log.info("Saldo-Neuberechnung beim Start ist deaktiviert.");
            return;
        }

        log.warn("ApplicationReady - starte explizit aktivierte Saldo-Neuberechnung.");
        timeTrackingService.rebuildAllUserBalancesOnce();
        log.info("Balance-Rebuild abgeschlossen.");
    }
}
