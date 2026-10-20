package com.chrono.chrono.services.pms;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="app.pms.rate-inheritance.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class PmsRateInheritanceScheduler {
 private final EntityManager em;private final PmsRateInheritanceService service;
 @Scheduled(fixedDelayString="${app.pms.rate-inheritance.scheduler-delay-ms:300000}",initialDelayString="${app.pms.rate-inheritance.scheduler-initial-delay-ms:60000}")
 public void run(){long after=0;while(true){var ids=em.createQuery("select r.id from PmsRateInheritance r where r.enabled=true and r.frozen=false and r.id>:after order by r.id",Long.class).setParameter("after",after).setMaxResults(100).getResultList();if(ids.isEmpty())return;for(Long id:ids){try{service.scheduled(id);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Rate inheritance failed for rule {}: {}",id,failure.getClass().getSimpleName());}after=id;}}}
}
