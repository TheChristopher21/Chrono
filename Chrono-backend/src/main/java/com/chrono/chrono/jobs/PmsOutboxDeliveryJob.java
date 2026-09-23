package com.chrono.chrono.jobs;

import com.chrono.chrono.services.pms.PmsOutboxProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
@ConditionalOnProperty(name = "app.pms.outbox.enabled", havingValue = "true")
public class PmsOutboxDeliveryJob {
    private final PmsOutboxProcessor processor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public PmsOutboxDeliveryJob(PmsOutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString = "${app.pms.outbox.interval-ms:10000}",
            initialDelayString = "${app.pms.outbox.initial-delay-ms:15000}")
    public void deliverPendingEvents() {
        processor.processDueEvents(workerId);
    }
}
