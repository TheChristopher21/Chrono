package com.chrono.chrono.jobs;

import com.chrono.chrono.services.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DemoDataCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataCleanupJob.class);

    private final DemoDataService demoDataService;

    public DemoDataCleanupJob(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @Scheduled(
            fixedDelayString = "${app.demo-login.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.demo-login.cleanup-initial-delay-ms:300000}"
    )
    public void cleanupExpiredDemoTenants() {
        int deleted = demoDataService.cleanupExpiredDemoTenants();
        if (deleted > 0) {
            logger.info("Deleted {} expired demo tenant(s).", deleted);
        }
    }
}
