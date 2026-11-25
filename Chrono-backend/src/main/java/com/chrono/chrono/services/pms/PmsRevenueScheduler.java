package com.chrono.chrono.services.pms;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="app.pms.revenue.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class PmsRevenueScheduler {
 private static final Logger LOG=LoggerFactory.getLogger(PmsRevenueScheduler.class);
 private final EntityManager em;private final PmsRevenueAutomationService service;
 @Scheduled(fixedDelayString="${app.pms.revenue.scheduler-delay-ms:300000}",initialDelayString="${app.pms.revenue.scheduler-initial-delay-ms:60000}")
 public void run(){long after=0;while(true){var ids=em.createQuery("select p.id from HotelProperty p where p.active=true and p.id>:after order by p.id",Long.class).setParameter("after",after).setMaxResults(100).getResultList();if(ids.isEmpty())return;for(Long id:ids){try{service.runProperty(id);}catch(RuntimeException failure){LOG.warn("Revenue automation failed for property {}: {}",id,failure.getClass().getSimpleName());}after=id;}}}
}
