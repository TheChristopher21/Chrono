package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.RefundPaymentRequest;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PmsApprovalService {
    private final PmsPropertyAccessService access; private final HotelPropertyRepository properties;
    private final PaymentRepository payments; private final PmsAuditWriter audit; private final EntityManager em;
    public record Policy(boolean enabled, BigDecimal refundThreshold, Long version) {}
    public record PolicyInput(boolean enabled,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal refundThreshold,Long expectedVersion) {}
    public record Request(@NotNull @Positive Long paymentId,@NotNull @Valid RefundPaymentRequest refund) {}
    public record Decision(boolean approve,@NotBlank @Size(max=500) String reason,long expectedVersion) {}
    public record View(Long id,long version,Long paymentId,String requestId,BigDecimal amount,String currency,String reason,Long cashShiftId,
            String requestedBy,String status,String decidedBy,String decisionReason,LocalDateTime createdAt,LocalDateTime expiresAt) {}
    public record PageView(List<View> items,int page,int size,long totalElements,boolean hasNext,String status) {}
    private enum ListStatus { OPEN, HISTORY, ALL }
    @Transactional(readOnly=true) public Policy policy(String username,Long propertyId) {
        var actor=access.access(username);access.require(actor,propertyId,"REFUNDS",false);var value=em.find(PmsApprovalPolicy.class,propertyId);
        return value==null ? new Policy(false,BigDecimal.valueOf(500),null) : new Policy(value.isEnabled(),value.getRefundThreshold(),value.getVersion());
    }
    @Transactional public Policy savePolicy(String username,Long propertyId,PolicyInput input) {
        var actor=access.requireMaster(username);var property=lock(actor.companyId(),propertyId);var value=em.find(PmsApprovalPolicy.class,propertyId);
        if(value!=null && !Objects.equals(value.getVersion(),input.expectedVersion())) throw conflict("Die Freigaberegel wurde inzwischen geändert.");
        if(value==null){value=new PmsApprovalPolicy();value.setPropertyId(propertyId);em.persist(value);}
        value.setEnabled(input.enabled());value.setRefundThreshold(PmsMoney.require(input.refundThreshold(),property.getCurrencyCode()));em.flush();
        audit.append(property,"approval.policy_changed","property",propertyId.toString(),"{\"enabled\":"+value.isEnabled()+",\"refundThreshold\":"+value.getRefundThreshold()+"}");
        return new Policy(value.isEnabled(),value.getRefundThreshold(),value.getVersion());
    }
    @Transactional(readOnly=true) public PageView list(String username,Long propertyId,int page,int size,String status) {
        var actor=access.access(username);access.require(actor,propertyId,"REFUNDS",false);
        if(page<0 || page>10000 || size<1 || size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Seite muss 0–10000 und Seitengröße 1–100 sein.");
        ListStatus filter;
        try {filter=ListStatus.valueOf(status==null?"OPEN":status.trim().toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException failure){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unbekannter Freigabestatus. OPEN, HISTORY oder ALL verwenden.");}
        // Count and page share one expiry boundary, including approvals that expired without a background job.
        LocalDateTime now=LocalDateTime.now();
        String predicate=" where a.property.id=:property";
        if(filter==ListStatus.OPEN)predicate+=" and a.status in ('PENDING','APPROVED') and a.expiresAt>:now";
        if(filter==ListStatus.HISTORY)predicate+=" and (a.status not in ('PENDING','APPROVED') or a.expiresAt<=:now)";
        var countQuery=em.createQuery("select count(a) from PmsFinancialApproval a"+predicate,Long.class).setParameter("property",propertyId);
        var pageQuery=em.createQuery("select a from PmsFinancialApproval a"+predicate+" order by a.id desc",PmsFinancialApproval.class).setParameter("property",propertyId);
        if(filter!=ListStatus.ALL){countQuery.setParameter("now",now);pageQuery.setParameter("now",now);}
        long total=countQuery.getSingleResult();
        var items=pageQuery.setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(this::view).toList();
        return new PageView(items,page,size,total,((long)page+1)*size<total,filter.name());
    }
    @Transactional public View request(String username,Long propertyId,Request request) {
        var actor=access.access(username);access.require(actor,propertyId,"REFUNDS",true);var property=lock(actor.companyId(),propertyId);
        var original=payments.findByIdForUpdate(request.paymentId(),propertyId,actor.companyId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var input=request.refund();validate(input,property.getCurrencyCode());
        if(original.getKind()!=PaymentKind.PAYMENT || original.getStatus()!=PaymentStatus.POSTED || input.amount().compareTo(original.getAmount())>0) throw conflict("Die Originalzahlung ist für diese Erstattung nicht geeignet.");
        String hash=fingerprint(property,request.paymentId(),input);var existing=find(propertyId,input.requestId());
        if(existing!=null){ if(!existing.getRequestHash().equals(hash)) throw conflict("Die Vorgangs-ID gehört zu anderen Erstattungsangaben.");return view(existing); }
        var row=new PmsFinancialApproval();row.setProperty(property);row.setPaymentId(request.paymentId());row.setRequestKey(input.requestId());row.setAmount(PmsMoney.require(input.amount(),property.getCurrencyCode()));row.setCurrency(property.getCurrencyCode());row.setReason(input.reason().trim());row.setCashShiftId(input.cashShiftId());row.setRequestHash(hash);row.setRequestedBy(username);row.setCreatedAt(LocalDateTime.now());row.setExpiresAt(LocalDateTime.now().plusHours(48));em.persist(row);em.flush();
        audit.append(property,"approval.requested","approval",row.getId().toString(),"{\"paymentId\":"+request.paymentId()+",\"amount\":"+row.getAmount()+"}");return view(row);
    }
    @Transactional public View decide(String username,Long propertyId,Long id,Decision input) {
        var actor=access.access(username);access.require(actor,propertyId,"REFUNDS",true);var property=lock(actor.companyId(),propertyId);
        var row=em.find(PmsFinancialApproval.class,id);
        if(row==null || !row.getProperty().getId().equals(propertyId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(row.getRequestedBy().equals(username))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Die antragstellende Person darf die eigene Erstattung nicht freigeben.");
        if(row.getVersion()!=input.expectedVersion() || !row.getStatus().equals("PENDING") || !row.getExpiresAt().isAfter(LocalDateTime.now()))throw conflict("Der Antrag ist bereits entschieden, abgelaufen oder geändert.");
        row.setStatus(input.approve()?"APPROVED":"REJECTED");row.setDecidedBy(username);row.setDecisionReason(input.reason().trim());row.setDecidedAt(LocalDateTime.now());em.flush();
        audit.append(property,"approval.decided","approval",id.toString(),"{\"status\":\""+row.getStatus()+"\"}");return view(row);
    }
    /** Called under the refund's existing hotel lock, immediately before persisting a new money intent. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consumeForRefund(HotelProperty property,Long paymentId,RefundPaymentRequest input) {
        var policy=em.find(PmsApprovalPolicy.class,property.getId());
        if(policy==null || !policy.isEnabled())return;
        BigDecimal previous=em.createQuery("select coalesce(sum(abs(p.amount)),0) from Payment p where p.originalPayment.id=:original and p.status in :statuses",BigDecimal.class)
                .setParameter("original",paymentId).setParameter("statuses",List.of(PaymentStatus.POSTED,PaymentStatus.PENDING)).getSingleResult();
        if(previous.add(input.amount()).compareTo(policy.getRefundThreshold())<0)return;
        var approval=find(property.getId(),input.requestId());
        if(approval==null || !"APPROVED".equals(approval.getStatus()) || !approval.getExpiresAt().isAfter(LocalDateTime.now()) || !approval.getRequestHash().equals(fingerprint(property,paymentId,input)))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,"Diese Erstattung benötigt die Freigabe einer zweiten berechtigten Person.");
        approval.setStatus("CONSUMED");approval.setConsumedAt(LocalDateTime.now());
        audit.append(property,"approval.consumed","approval",approval.getId().toString(),"{\"paymentId\":"+paymentId+"}");
    }
    private HotelProperty lock(Long company,Long property){return properties.findByIdAndCompany_IdForUpdate(property,company).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));}
    private PmsFinancialApproval find(Long property,String key){return em.createQuery("select a from PmsFinancialApproval a where a.property.id=:property and a.requestKey=:key",PmsFinancialApproval.class).setParameter("property",property).setParameter("key",key).getResultStream().findFirst().orElse(null);}
    private void validate(RefundPaymentRequest input,String currency){if(input.requestId()==null || !input.requestId().matches("[A-Za-z0-9._:-]{8,80}") || input.reason()==null || input.reason().trim().length()<3 || PmsMoney.require(input.amount(),currency).signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Betrag, Begründung und eindeutige Vorgangs-ID erforderlich.");}
    private String fingerprint(HotelProperty property,Long payment,RefundPaymentRequest input){
        String[] fields={payment.toString(),PmsMoney.require(input.amount(),property.getCurrencyCode()).toPlainString(),property.getCurrencyCode(),Objects.toString(input.reason(),"").trim(),Objects.toString(input.cashShiftId(),""),input.requestId()};
        StringBuilder canonical=new StringBuilder();for(String field:fields)canonical.append(field.length()).append(':').append(field);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}
    }
    private View view(PmsFinancialApproval value){String status=value.getStatus();if(Set.of("PENDING","APPROVED").contains(status)&&!value.getExpiresAt().isAfter(LocalDateTime.now()))status="EXPIRED";return new View(value.getId(),value.getVersion(),value.getPaymentId(),value.getRequestKey(),value.getAmount(),value.getCurrency(),value.getReason(),value.getCashShiftId(),value.getRequestedBy(),status,value.getDecidedBy(),value.getDecisionReason(),value.getCreatedAt(),value.getExpiresAt());}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
