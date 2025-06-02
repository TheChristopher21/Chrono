package com.chrono.chrono.jobs;

import com.chrono.chrono.services.TimeTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class NightlyAutoEndJob {

    private static final Logger logger = LoggerFactory.getLogger(NightlyAutoEndJob.class);

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Scheduled(cron = "0 30 23 * * *", zone = "Europe/Zurich")
    @Transactional
    public void performAutoEndForForgottenPunches() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Zurich")); 
        logger.info("NightlyAutoEndJob gestartet für Datum: {}", today);
        try {
            timeTrackingService.autoEndDayForUsersWhoForgotPunchOut(today);
            logger.info("NightlyAutoEndJob erfolgreich beendet für Datum: {}", today);
        } catch (Exception e) {
            logger.error("Fehler im NightlyAutoEndJob für Datum {}: {}", today, e.getMessage(), e);
        }
    }
}
