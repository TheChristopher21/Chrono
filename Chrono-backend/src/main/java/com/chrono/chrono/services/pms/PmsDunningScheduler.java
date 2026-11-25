package com.chrono.chrono.services.pms;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
@Service
@ConditionalOnProperty(name="app.pms.billing.scheduler.enabled",havingValue="true",matchIfMissing=true)
public class PmsDunningScheduler {
 private final PmsBillingSettingsRepository settings; private final HotelPropertyRepository properties;
 private final PmsDunningService dunning; private final TransactionTemplate tx;
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PmsDunningScheduler.class);
 public PmsDunningScheduler(PmsBillingSettingsRepository settings,HotelPropertyRepository properties,PmsDunningService dunning,PlatformTransactionManager manager){this.settings=settings;this.properties=properties;this.dunning=dunning;tx=new TransactionTemplate(manager);}
 @Scheduled(fixedDelayString="${app.pms.billing.scheduler.interval-ms:300000}")
 public void run(){for(Long id:settings.automaticReminderProperties())try{
  Long companyId=tx.execute(status->properties.findById(id).filter(p->p.isActive()).map(p->p.getCompany().getId()).orElse(null));
  if(companyId!=null)dunning.run(companyId,id,"system");
 }catch(RuntimeException error){log.warn("PMS reminder run failed for property {}: {}",id,error.getClass().getSimpleName());}}
}
