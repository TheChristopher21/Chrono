package com.chrono.chrono.jobs;
import com.chrono.chrono.services.pms.PmsPaymentAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="app.pms.payments.automation.enabled",havingValue="true")
public class PmsPaymentAutomationJob {
    private final PmsPaymentAutomationService automation;
    public PmsPaymentAutomationJob(PmsPaymentAutomationService automation){this.automation=automation;}
    @Scheduled(fixedDelayString="${app.pms.payments.automation.interval-ms:60000}",initialDelayString="${app.pms.payments.automation.initial-delay-ms:30000}")
    public void reconcile(){automation.run();}
}
