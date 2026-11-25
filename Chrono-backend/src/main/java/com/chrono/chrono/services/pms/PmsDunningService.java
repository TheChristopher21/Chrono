package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsBillingAutomationDtos.*;
import com.chrono.chrono.dto.pms.PmsDeliveryDtos.Attachment;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class PmsDunningService {
 private final HotelPropertyRepository properties;
 private final PmsReceivableRepository receivables;
 private final PmsDunningNoticeRepository notices;
 private final PmsDeliveryJobRepository jobs;
 private final PmsBillingSettingsService settings;
 private final PmsFinancialPeriodService periods;
 private final PmsDeliveryService delivery;
 private final ObjectProvider<PmsAdvancedService> advanced;
 private final PmsAuditWriter audit;
 private final TransactionTemplate tx;
 public PmsDunningService(HotelPropertyRepository properties,PmsReceivableRepository receivables,PmsDunningNoticeRepository notices,PmsDeliveryJobRepository jobs,
      PmsBillingSettingsService settings,PmsFinancialPeriodService periods,PmsDeliveryService delivery,ObjectProvider<PmsAdvancedService> advanced,PmsAuditWriter audit,PlatformTransactionManager manager){
  this.properties=properties;this.receivables=receivables;this.notices=notices;this.jobs=jobs;this.settings=settings;this.periods=periods;this.delivery=delivery;this.advanced=advanced;this.audit=audit;
  tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 @Transactional(readOnly=true)
 public List<ReminderCandidate> preview(Long companyId,Long propertyId){
  HotelProperty property=property(companyId,propertyId,false);PmsBillingSettings config=settings.effective(property);
  LocalDate date=effectiveDay(property);
  return receivables.searchPage(propertyId,null,true,false,date,"",PageRequest.of(0,100)).getContent().stream().map(r->candidate(r,config,date)).toList();
 }
 @Transactional(readOnly=true)
 public List<ReminderView> history(Long propertyId){return notices.findByProperty_IdOrderByIdDesc(propertyId,PageRequest.of(0,100)).map(this::view).getContent();}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public ReminderRun run(Long companyId,Long propertyId,String actor){
  List<Long> ids=tx.execute(status->{
   HotelProperty property=property(companyId,propertyId,false);LocalDate date=effectiveDay(property);
   List<Long> result=new ArrayList<>();int page=0;boolean more;
   do{var batch=receivables.searchPage(propertyId,null,true,true,date,"",PageRequest.of(page++,100));batch.getContent().forEach(r->result.add(r.getId()));more=batch.hasNext();}while(more);
   return result;
  });
  List<ReminderView> created=new ArrayList<>();List<String> skipped=new ArrayList<>();
  for(Long id:ids){
   try{ReminderView notice=tx.execute(status->issue(companyId,propertyId,id,actor));if(notice!=null)created.add(notice);}
   catch(ResponseStatusException error){skipped.add("Forderung "+id+": "+error.getReason());}
  }
  return new ReminderRun(created.size(),List.copyOf(created),List.copyOf(skipped));
 }
 private ReminderView issue(Long companyId,Long propertyId,Long id,String actor){
  HotelProperty property=property(companyId,propertyId,true);PmsBillingSettings config=settings.effective(property);
  LocalDate date=effectiveDay(property);PmsReceivable r=receivables.findByIdAndProperty_Id(id,propertyId).orElseThrow();
  ReminderCandidate candidate=candidate(r,config,date);if(!candidate.eligible())return null;
  PmsDunningNotice notice=new PmsDunningNotice();notice.setProperty(property);notice.setReceivable(r);notice.setInvoice(r.getInvoice());notice.setLevel(candidate.nextLevel());
  notice.setBusinessDate(date);notice.setDueDate(r.getDueDate());notice.setOutstandingAmount(r.balance());notice.setCurrencyCode(r.getInvoice().getCurrencyCode());
  notice.setCreatedAt(LocalDateTime.now());notice.setCreatedBy(actor);notices.saveAndFlush(notice);
  if(config.isSendReminders()){
   PmsInvoice invoice=r.getInvoice();Map<String,String> values=delivery.invoiceValues(invoice,r.balance(),notice.getLevel());
   byte[] pdf=advanced.getObject().generateInvoicePdf(property.getCompany(),invoice.getId());
   var queued=delivery.enqueue(property,"REMINDER","reminder:"+notice.getId(),delivery.invoiceRecipient(invoice,null),
       PmsDeliveryService.render(config.getReminderSubject(),values),PmsDeliveryService.render(config.getReminderBody(),values),
       List.of(new Attachment(invoice.getInvoiceNumber()+".pdf","application/pdf",pdf)),invoice,null,actor);
   PmsDeliveryJob job=jobs.findById(queued.id()).orElseThrow();job.setReceivable(r);job.setOutstandingAtQueue(r.balance());notice.setDelivery(job);
  }
  r.setReminderLevel(notice.getLevel());r.setLastReminderAt(LocalDateTime.now());receivables.save(r);
  audit.append(property,"receivable.reminder_issued","receivable",id.toString(),"{\"level\":"+notice.getLevel()+",\"amount\":"+r.balance()+"}");
  return view(notice);
 }
 private ReminderCandidate candidate(PmsReceivable r,PmsBillingSettings config,LocalDate date){
  PmsDunningNotice last=notices.findTopByReceivable_IdOrderByLevelDesc(r.getId()).orElse(null);
  int level=Math.max(r.getReminderLevel(),last==null?0:last.getLevel())+1;
  LocalDate eligible=r.getDueDate().plusDays(config.getFirstReminderDays());
  LocalDate previous=last==null?(r.getLastReminderAt()==null?null:r.getLastReminderAt().toLocalDate()):last.getBusinessDate();
  if(previous!=null&&previous.plusDays(config.getReminderIntervalDays()).isAfter(eligible))eligible=previous.plusDays(config.getReminderIntervalDays());
  String reason=r.balance().signum()<=0?"Kein offener Betrag":r.getInvoice().getStatus()==InvoiceStatus.CREDITED?"Korrigierte Rechnung":
      level>config.getMaxReminders()?"Maximale Mahnstufe erreicht":date.isBefore(eligible)?"Mahnfrist noch nicht erreicht":"";
  return new ReminderCandidate(r.getId(),r.getInvoice().getInvoiceNumber(),r.getOrganization().getName(),r.getDueDate(),r.balance(),r.getInvoice().getCurrencyCode(),level,eligible,reason.isEmpty(),reason);
 }
 private LocalDate effectiveDay(HotelProperty p){LocalDate date=periods.readBusinessDate(p),calendar=LocalDate.now(ZoneId.of(p.getTimezone()));return date.isAfter(calendar)?calendar:date;}
 private ReminderView view(PmsDunningNotice n){return new ReminderView(n.getId(),n.getReceivable().getId(),n.getInvoice().getInvoiceNumber(),n.getLevel(),n.getBusinessDate(),n.getOutstandingAmount(),n.getCurrencyCode(),n.getDelivery()==null?null:n.getDelivery().getId());}
 private HotelProperty property(Long companyId,Long propertyId,boolean lock){return (lock?properties.findByIdAndCompany_IdForUpdate(propertyId,companyId):properties.findByIdAndCompany_Id(propertyId,companyId)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));}
}
