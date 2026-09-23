package com.chrono.chrono.jobs;
import com.chrono.chrono.services.pms.PmsBeds24Service;import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="app.pms.beds24.worker.enabled",havingValue="true")
public class PmsBeds24DeliveryJob {
 private final PmsBeds24Service service;public PmsBeds24DeliveryJob(PmsBeds24Service service){this.service=service;}
 @Scheduled(fixedDelayString="${app.pms.beds24.worker.interval-ms:30000}",initialDelayString="${app.pms.beds24.worker.initial-delay-ms:30000}")public void deliver(){service.processDue();}
}
