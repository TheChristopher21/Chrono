package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.PmsDeliveryJobRepository;
import com.chrono.chrono.repositories.pms.PmsBillingSettingsRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.*;

@Service
@ConditionalOnProperty(name="app.pms.delivery.worker.enabled",havingValue="true",matchIfMissing=true)
public class PmsDeliveryWorker {
    private final PmsDeliveryJobRepository jobs;
    private final JavaMailSender sender;
    private final TransactionTemplate tx;
    private final PmsAuditWriter audit;
    private final PmsBillingSettingsRepository settings;
    private final HotelPropertyRepository properties;
    public PmsDeliveryWorker(PmsDeliveryJobRepository jobs,JavaMailSender sender,PlatformTransactionManager transactions,PmsAuditWriter audit,PmsBillingSettingsRepository settings,HotelPropertyRepository properties){
        this.jobs=jobs;this.sender=sender;this.tx=new TransactionTemplate(transactions);this.audit=audit;this.settings=settings;this.properties=properties;
    }
    @Scheduled(fixedDelayString="${app.pms.delivery.worker.interval-ms:30000}")
    public void scheduled(){runBatch();}
    public int runBatch(){
        List<Long> due=tx.execute(status->jobs.due(LocalDateTime.now(),PageRequest.of(0,20)));
        int processed=0;
        for(Long id:due){try{if(process(id))processed++;}catch(RuntimeException ignored){/* Lease expiry surfaces unknown status; never blindly resend after a database failure. */}}
        return processed;
    }
    public boolean process(Long id){
        String owner=UUID.randomUUID().toString();
        PmsDeliveryJob claimed=tx.execute(status->{
            lockProperty(id);
            PmsDeliveryJob j=jobs.locked(id).orElse(null);if(j==null)return null;
            LocalDateTime now=LocalDateTime.now();
            if("SENDING".equals(j.getStatus())){
                if(j.getLeaseUntil()!=null&&j.getLeaseUntil().isBefore(now)){
                    j.setStatus("UNKNOWN");j.setLastError("Versand wurde unterbrochen; Annahme beim Mailserver vor erneuter Zustellung prüfen.");jobs.save(j);syncCommunication(j);
                }
                return null;
            }
            if(!Set.of("QUEUED","RETRY").contains(j.getStatus())||j.getNextAttemptAt().isAfter(now))return null;
            if(!settings.findById(j.getProperty().getId()).map(PmsBillingSettings::isMailEnabled).orElse(false)){
                j.setStatus("PAUSED");j.setLastError("Hotelversand wurde deaktiviert. Nach Aktivierung ausdrücklich erneut starten.");jobs.save(j);return null;
            }
            if("INVOICE".equals(j.getKind()) && j.getInvoice()!=null && j.getInvoice().getType()==InvoiceType.INVOICE && j.getInvoice().getStatus()==InvoiceStatus.CREDITED){
                j.setStatus("CANCELLED");j.setLastError("Die Rechnung wurde vor dem Versand korrigiert. Gutschrift oder Ersatzrechnung auswählen.");jobs.save(j);return null;
            }
            if(j.getReceivable()!=null){
                PmsReceivable receivable=j.getReceivable();
                java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of(j.getProperty().getTimezone()));
                if(receivable.balance().signum()<=0||receivable.getInvoice().getStatus()==InvoiceStatus.CREDITED||!receivable.getDueDate().isBefore(today)
                    ||j.getInvoice()==null||j.getInvoice().getStatus()==InvoiceStatus.CREDITED||!j.getInvoice().getId().equals(receivable.getInvoice().getId())
                    ||j.getOutstandingAtQueue()==null||receivable.balance().compareTo(j.getOutstandingAtQueue())!=0){
                    j.setStatus("CANCELLED");j.setLastError("Mahnung verworfen: Forderung, Fälligkeit oder offener Betrag hat sich geändert.");jobs.save(j);return null;
                }
            }
            j.setStatus("SENDING");j.setAttempts(j.getAttempts()+1);j.setLeaseOwner(owner);j.setLeaseUntil(now.plusMinutes(5));
            j.getAttachments().forEach(a->a.getContent().clone());jobs.saveAndFlush(j);return j;
        });
        if(claimed==null)return false;
        String outcome="SENT";String failure=null;
        try{
            MimeMessage message=sender.createMimeMessage();
            MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
            if(claimed.getSenderName()==null)helper.setFrom(claimed.getSenderEmail());else helper.setFrom(claimed.getSenderEmail(),claimed.getSenderName());
            if(claimed.getReplyTo()!=null)helper.setReplyTo(claimed.getReplyTo());
            helper.setTo(claimed.getRecipient());helper.setSubject(claimed.getSubject());helper.setText(claimed.getBody(),false);
            for(PmsDeliveryAttachment a:claimed.getAttachments())helper.addAttachment(a.getFilename(),new ByteArrayResource(a.getContent()),a.getContentType());
            message.setHeader("Message-ID",claimed.getMessageId());
            message.setHeader("X-Chrono-Delivery-ID",claimed.getId().toString());
            // JavaMailSenderImpl preserves an explicitly set Message-ID after saveChanges().
            sender.send(message);
        }catch(Exception error){outcome=classify(error);failure=summary(error);}
        String result=outcome;String lastError=failure;
        tx.executeWithoutResult(status->{
            lockProperty(id);
            PmsDeliveryJob j=jobs.locked(id).orElseThrow();
            if(!"SENDING".equals(j.getStatus())||!owner.equals(j.getLeaseOwner()))return;
            j.setStatus("RETRY".equals(result)&&j.getAttempts()>=5?"FAILED":result);j.setLastError(lastError);
            j.setLeaseUntil(null);j.setLeaseOwner(null);
            if("SENT".equals(j.getStatus()))j.setSentAt(LocalDateTime.now());
            else if("RETRY".equals(j.getStatus()))j.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60,1L<<Math.min(6,j.getAttempts()-1))));
            jobs.save(j);syncCommunication(j);
            audit.append(j.getProperty(),"delivery.status_changed","delivery",id.toString(),"{\"status\":\""+j.getStatus()+"\",\"attempts\":"+j.getAttempts()+"}");
        });
        return true;
    }
    // Read identifiers only before the property lock: caching a mutable job here could retain a stale QUEUED state.
    private void lockProperty(Long id){jobs.scope(id).ifPresent(scope->properties.findByIdAndCompany_IdForUpdate(scope.getPropertyId(),scope.getCompanyId()).orElseThrow());}
    private void syncCommunication(PmsDeliveryJob job){
        GuestCommunication communication=job.getCommunication();if(communication==null)return;
        if("SENT".equals(job.getStatus())){communication.setStatus(CommunicationStatus.SENT);communication.setSentAt(job.getSentAt());}
        else if(Set.of("FAILED","UNKNOWN").contains(job.getStatus()))communication.setStatus(CommunicationStatus.FAILED);
        else communication.setStatus(CommunicationStatus.QUEUED);
    }
    static String classify(Throwable error){
        if(error instanceof MailSendException send && send.getMessageExceptions().length==1)return classify(send.getMessageExceptions()[0]);
        for(Throwable current=error;current!=null;current=current.getCause()){
            if(current instanceof MailAuthenticationException||current instanceof MailPreparationException)return "FAILED";
            if(current instanceof java.net.ConnectException||current instanceof java.net.UnknownHostException)return "RETRY";
            try{Object code=current.getClass().getMethod("getReturnCode").invoke(current);if(code instanceof Integer value){if(value>=400&&value<500)return "RETRY";if(value>=500)return "FAILED";}}catch(ReflectiveOperationException ignored){}
            if(current instanceof SendFailedException rejected&&rejected.getValidSentAddresses()==null&&rejected.getInvalidAddresses()!=null)return "FAILED";
            if(current instanceof MessagingException messaging && messaging.getNextException()!=null && messaging.getNextException()!=current.getCause())return classify(messaging.getNextException());
        }
        return "UNKNOWN";
    }
    private String summary(Exception error){
        String value=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"Mailserverstatus unklar.");
        return value.substring(0,Math.min(1000,value.length()));
    }
}
