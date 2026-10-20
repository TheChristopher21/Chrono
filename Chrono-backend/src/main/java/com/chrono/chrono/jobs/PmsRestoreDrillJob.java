package com.chrono.chrono.jobs;

import com.chrono.chrono.services.pms.PmsRestoreDrillService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.backup.restore-test.enabled", havingValue = "true")
public class PmsRestoreDrillJob {
    private final PmsRestoreDrillService restoreDrillService;

    public PmsRestoreDrillJob(PmsRestoreDrillService restoreDrillService) {
        this.restoreDrillService = restoreDrillService;
    }

    @Scheduled(cron = "${app.backup.restore-test.cron:0 30 2 * * SUN}")
    public void verifyLatestBackup() {
        restoreDrillService.runDrill();
    }
}
