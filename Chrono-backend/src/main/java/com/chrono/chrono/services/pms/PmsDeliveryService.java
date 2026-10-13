package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsDeliveryDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional
public class PmsDeliveryService {
    private final PmsDeliveryJobRepository jobs;
    private final HotelPropertyRepository properties;
    private final ReservationRepository reservations;
    private final PmsInvoiceRepository invoices;
    private final PmsBillingSettingsService settings;
    private final ObjectProvider<PmsAdvancedService> advanced;
    private final PmsAuditWriter audit;
    public PmsDeliveryService(PmsDeliveryJobRepository jobs,HotelPropertyRepository properties,ReservationRepository reservations,
        PmsInvoiceRepository invoices,PmsBillingSettingsService settings,ObjectProvider<PmsAdvancedService> advanced,PmsAuditWriter audit){
        this.jobs=jobs;this.properties=properties;this.reservations=reservations;this.invoices=invoices;this.settings=settings;this.advanced=advanced;this.audit=audit;
    }
    @Transactional(readOnly=true)
    public DeliveryPage list(Long propertyId,int page,int size) {
        if(page<0||size<1||size>100)throw invalid("Ungültige Seitenauswahl.");
        var result=jobs.findByProperty_IdOrderByCreatedAtDescIdDesc(propertyId,PageRequest.of(page,size));
        return new DeliveryPage(result.getContent().stream().map(this::view).toList(),page,size,result.getTotalElements(),result.hasNext());
    }
    public DeliveryView queueInvoice(Long companyId,Long propertyId,Long invoiceId,InvoiceSend request,String actor) {
        HotelProperty property=property(companyId,propertyId);
        PmsInvoice invoice=invoices.findByIdAndProperty_Company_Id(invoiceId,companyId)
            .filter(i->i.getProperty().getId().equals(propertyId)).orElseThrow(()->missing("Rechnung nicht gefunden."));
        String recipient=invoiceRecipient(invoice,request.recipient());
        String key="invoice:"+key(request.requestId());
        var existing=jobs.findByProperty_IdAndRequestKey(propertyId,key);
        if(existing.isPresent()){PmsDeliveryJob job=existing.get();if(job.getInvoice()==null||!job.getInvoice().getId().equals(invoiceId)||!job.getRecipient().equals(recipient))throw invalid("Versand-ID gehört zu einer anderen Rechnung oder Empfängeradresse.");return view(job);}
        PmsBillingSettings config=settings.effective(property);
        Map<String,String> values=invoiceValues(invoice,invoice.getGrossAmount(),null);
        byte[] pdf=advanced.getObject().generateInvoicePdf(property.getCompany(),invoiceId);
        return enqueue(property,"INVOICE",key,recipient,render(config.getInvoiceSubject(),values),render(config.getInvoiceBody(),values),
            List.of(new Attachment(invoice.getInvoiceNumber()+".pdf","application/pdf",pdf)),invoice,null,actor);
    }
    public void automaticallyQueueInvoice(PmsInvoice invoice) {
        if(!settings.effective(invoice.getProperty()).isAutomaticInvoices())return;
        queueInvoice(invoice.getProperty().getCompany().getId(),invoice.getProperty().getId(),invoice.getId(),
            new InvoiceSend("automatic:"+invoice.getId(),null),"system");
    }
    public DeliveryView queuePaymentLink(Long companyId,Long propertyId,Long reservationId,String requestId,String checkoutUrl,BigDecimal amount,String currencyCode) {
        HotelProperty property=property(companyId,propertyId);
        Reservation reservation=reservations.findByIdAndProperty_Company_Id(reservationId,companyId)
            .filter(r->r.getProperty().getId().equals(propertyId)).orElseThrow(()->missing("Reservierung nicht gefunden."));
        if(!reservation.getCurrencyCode().equals(currencyCode))throw invalid("Zahlungslink und Aufenthalt haben unterschiedliche Währungen.");
        if(PmsMoney.require(amount,currencyCode).signum()<=0)throw invalid("Ein Zahlungslink benötigt einen positiven Betrag.");
        java.net.URI uri;
        try{uri=java.net.URI.create(checkoutUrl);}catch(Exception bad){throw invalid("Ungültiger Zahlungslink.");}
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||checkoutUrl.length()>3000)throw invalid("Zahlungslink benötigt eine gültige HTTPS-Adresse.");
        var billing=PmsProfileData.billing(reservation.getGuest().getBillingProfile());
        String recipient=billing!=null&&billing.billingEmail()!=null&&!billing.billingEmail().isBlank()?billing.billingEmail():reservation.getGuest().getEmail();
        String body="Reservation "+reservation.getConfirmationCode()+"\nAmount: "+PmsMoney.require(amount,currencyCode).toPlainString()+" "+currencyCode+"\n\n"+checkoutUrl;
        return enqueue(property,"PAYMENT_LINK","payment:"+key(requestId),recipient,"Payment link · "+property.getName(),body,List.of(),null,null,"system");
    }
    public DeliveryView queueCommunication(GuestCommunication communication) {
        if(communication.getChannel()!=CommunicationChannel.EMAIL)throw invalid("SMTP-Versand unterstützt E-Mail-Nachrichten.");
        return enqueue(property(communication.getProperty().getCompany().getId(),communication.getProperty().getId()),"COMMUNICATION",
            "communication:"+communication.getId(),communication.getRecipient(),communication.getSubject(),communication.getBody(),List.of(),null,communication,"system");
    }
    public void queueCommunicationIfEnabled(GuestCommunication communication) {
        if(communication.getChannel()==CommunicationChannel.EMAIL && settings.effective(communication.getProperty()).isMailEnabled()) queueCommunication(communication);
    }
    public DeliveryView enqueue(HotelProperty property,String kind,String requestKey,String recipient,String subject,String body,
        List<Attachment> attachments,PmsInvoice invoice,GuestCommunication communication,String actor) {
        property=property(property.getCompany().getId(),property.getId());
        PmsBillingSettings config=settings.effective(property);
        if(!config.isMailEnabled())throw invalid("E-Mail-Versand ist für dieses Hotel nicht aktiviert.");
        recipient=PmsBillingSettingsService.email(recipient);
        String sender=PmsBillingSettingsService.email(config.getSenderEmail());
        PmsBillingSettingsService.header(subject);
        if(subject==null||subject.isBlank()||subject.length()>240||body==null||body.isBlank()||body.length()>16000||attachments.size()>10)throw invalid("Nachricht oder Anhänge sind zu umfangreich.");
        if(requestKey==null || !requestKey.matches("[A-Za-z0-9._:-]{1,160}"))throw invalid("Ungültige Versand-ID.");
        StringBuilder payload=new StringBuilder(recipient).append('\0').append(sender).append('\0').append(config.getSenderName()).append('\0').append(config.getReplyTo()).append('\0').append(subject).append('\0').append(body);
        long total=0;
        for(Attachment attachment:attachments){
            if(attachment.content()==null||attachment.filename()==null||!attachment.filename().matches("[\\p{L}\\p{N} ._-]{1,180}")||
               !Set.of("application/pdf","image/png","image/jpeg","text/csv","text/plain").contains(attachment.contentType()))throw invalid("Ungültiger Dateianhang.");
            total+=attachment.content().length;if(total>8*1024*1024)throw invalid("Anhänge dürfen zusammen höchstens 8 MB umfassen.");
            payload.append('\0').append(attachment.filename()).append('\0').append(attachment.contentType()).append('\0').append(sha256(attachment.content()));
        }
        String hash=sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
        var existing=jobs.findByProperty_IdAndRequestKey(property.getId(),requestKey);
        if(existing.isPresent()){if(!existing.get().getPayloadHash().equals(hash))throw invalid("Diese Versand-ID wurde bereits für andere Inhalte verwendet.");return view(existing.get());}
        PmsDeliveryJob job=new PmsDeliveryJob();job.setProperty(property);job.setKind(kind);job.setRequestKey(requestKey);job.setRecipient(recipient);
        job.setSenderEmail(sender);job.setSenderName(config.getSenderName());job.setReplyTo(config.getReplyTo());job.setSubject(subject);job.setBody(body);
        job.setInvoice(invoice);job.setCommunication(communication);job.setPayloadHash(hash);job.setCreatedAt(LocalDateTime.now());job.setCreatedBy(actor);
        job.setMessageId("<pms-"+UUID.randomUUID()+"@"+sender.substring(sender.indexOf('@')+1)+">");job.setNextAttemptAt(LocalDateTime.now());
        for(Attachment input:attachments){PmsDeliveryAttachment a=new PmsDeliveryAttachment();a.setJob(job);a.setFilename(input.filename());a.setContentType(input.contentType());a.setContent(input.content().clone());a.setSha256(sha256(input.content()));job.getAttachments().add(a);}
        jobs.saveAndFlush(job);audit.append(property,"delivery.queued","delivery",job.getId().toString(),"{\"kind\":\""+kind+"\"}");
        return view(job);
    }
    public DeliveryView retry(Long companyId,Long propertyId,Long id,Retry request,String actor){
        HotelProperty property=property(companyId,propertyId);PmsDeliveryJob job=jobs.locked(id).filter(j->j.getProperty().getId().equals(propertyId)).orElseThrow(()->missing("Versandauftrag nicht gefunden."));
        if(!Set.of("FAILED","UNKNOWN","RETRY","PAUSED").contains(job.getStatus()))throw invalid("Dieser Versandauftrag kann nicht erneut gestartet werden.");
        if(!settings.effective(property).isMailEnabled())throw invalid("Hotelversand muss zuerst aktiviert werden.");
        if("UNKNOWN".equals(job.getStatus())&&!request.acknowledgePossibleDuplicate())throw invalid("Ein unklarer Versand muss vor erneuter Zustellung geprüft werden; mögliche Doppelzustellung bestätigen.");
        job.setStatus("QUEUED");job.setAttempts(0);job.setLeaseOwner(null);job.setLeaseUntil(null);job.setNextAttemptAt(LocalDateTime.now());job.setLastError(null);
        jobs.save(job);audit.append(property,"delivery.retry_requested","delivery",id.toString(),"{\"possibleDuplicateAcknowledged\":"+request.acknowledgePossibleDuplicate()+"}");return view(job);
    }
    public String invoiceRecipient(PmsInvoice invoice,String override){
        if(override!=null&&!override.isBlank())return PmsBillingSettingsService.email(override);
        var billing=PmsProfileData.billing(invoice.getRecipientSnapshot());
        if(billing!=null&&billing.billingEmail()!=null&&!billing.billingEmail().isBlank())return PmsBillingSettingsService.email(billing.billingEmail());
        if(invoice.getFolio().getOrganization()==null)return PmsBillingSettingsService.email(invoice.getFolio().getReservation().getGuest().getEmail());
        throw invalid("Für diese Firmenrechnung muss eine Rechnungs-E-Mail-Adresse gespeichert oder ausdrücklich ausgewählt werden.");
    }
    public Map<String,String> invoiceValues(PmsInvoice invoice,BigDecimal amount,Integer level){
        return Map.of("hotelName",invoice.getProperty().getName(),"invoiceNumber",invoice.getInvoiceNumber(),"amount",amount.toPlainString(),
            "currency",invoice.getCurrencyCode(),"dueDate",invoice.getDueDate().toString(),"recipientName",invoice.getRecipientName(),"level",level==null?"":level.toString());
    }
    public static String render(String template,Map<String,String> values){String result=template;for(var entry:values.entrySet())result=result.replace("{{"+entry.getKey()+"}}",entry.getValue());return result;}
    public DeliveryView view(PmsDeliveryJob j){return new DeliveryView(j.getId(),j.getInvoice()==null?null:j.getInvoice().getId(),j.getRequestKey(),j.getKind(),j.getRecipient(),j.getSenderEmail(),j.getSubject(),j.getStatus(),j.getAttempts(),j.getNextAttemptAt(),j.getCreatedAt(),j.getSentAt(),j.getLastError(),j.getMessageId(),
        j.getAttachments().stream().map(a->new AttachmentView(a.getId(),a.getFilename(),a.getContentType(),a.getContent().length,a.getSha256())).toList());}
    public static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private String key(String value){if(value==null||!value.matches("[A-Za-z0-9._:-]{1,120}"))throw invalid("Ungültige eindeutige Vorgangs-ID.");return value;}
    private HotelProperty property(Long companyId,Long id){return properties.findByIdAndCompany_IdForUpdate(id,companyId).orElseThrow(()->missing("Hotel nicht gefunden."));}
    private static ResponseStatusException invalid(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
    private static ResponseStatusException missing(String text){return new ResponseStatusException(HttpStatus.NOT_FOUND,text);}
}
