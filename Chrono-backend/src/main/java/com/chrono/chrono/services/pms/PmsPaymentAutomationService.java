package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.chrono.chrono.dto.pms.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class PmsPaymentAutomationService {
    private static final List<String> OPEN=List.of("REQUESTED","OPEN","AUTHORIZED","CAPTURE_REQUESTED","RELEASE_REQUESTED");
    private final PmsPaymentRequestRepository requests;private final PaymentRepository payments;private final PmsStripeEventRepository events;
    private final PmsPaymentRequestService checkout;private final PmsRefundProcessor refunds;private final PmsPaymentSettingsRepository settings;
    private final ReservationRepository reservations;private final FolioRepository folios;private final HotelPropertyRepository properties;
    private final PmsReservationPolicyService policies;private final PmsDeliveryService delivery;private final TransactionTemplate tx;
    private final boolean enabled;
    public PmsPaymentAutomationService(PmsPaymentRequestRepository requests,PaymentRepository payments,PmsStripeEventRepository events,
            PmsPaymentRequestService checkout,PmsRefundProcessor refunds,PmsPaymentSettingsRepository settings,ReservationRepository reservations,
            FolioRepository folios,HotelPropertyRepository properties,PmsReservationPolicyService policies,PmsDeliveryService delivery,
            PlatformTransactionManager transactions,@Value("${app.pms.payments.automation.enabled:false}") boolean enabled){
        this.requests=requests;this.payments=payments;this.events=events;this.checkout=checkout;this.refunds=refunds;this.settings=settings;
        this.reservations=reservations;this.folios=folios;this.properties=properties;this.policies=policies;this.delivery=delivery;this.tx=new TransactionTemplate(transactions);this.enabled=enabled;
    }
    public record RequestStatus(Long id,Long folioId,String requestId,String status,String merchantContext,String error,LocalDateTime checkedAt) {}
    public record LegacyPayment(Long id,Long folioId,java.math.BigDecimal amount){}
    public record Status(boolean workerEnabled,LocalDateTime lastDepositScan,String depositError,List<RequestStatus> requests,List<LegacyPayment> legacyPayments) {}
    public Status status(Long companyId,Long propertyId){return tx.execute(s -> {
        properties.findByIdAndCompany_Id(propertyId,companyId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));
        var config=settings.findById(propertyId).orElse(new PmsPaymentSettings());
        return new Status(enabled,config.getLastAutomationAt(),config.getLastAutomationError(),requests.findTop100ByFolio_Reservation_Property_IdOrderByCreatedAtDesc(propertyId).stream()
                .map(r -> new RequestStatus(r.getId(),r.getFolio().getId(),r.getRequestKey(),r.getStatus(),r.getMerchantContext(),r.getAutomationError(),r.getLastCheckedAt())).toList(),
                payments.findTop100ByFolio_Reservation_Property_IdAndMethodAndKindAndMerchantContextIsNullOrderByIdDesc(propertyId,PaymentMethod.CARD,PaymentKind.PAYMENT).stream().map(p -> new LegacyPayment(p.getId(),p.getFolio().getId(),p.getAmount())).toList());
    });}
    public void run(){
        for(PmsStripeEvent candidate:events.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(List.of("PENDING","PROCESSING"),LocalDateTime.now())) processEvent(candidate.getId());
        for(PmsPaymentRequest row:requests.findTop50ByStatusInOrderByLastCheckedAtAscIdAsc(OPEN)) {try{reconcile(row.getId());}catch(Exception ignored){ /* Individual failure must not block other hotels. */ }}
        for(Payment row:payments.findTop50ByStatusAndKindOrderByProviderCheckedAtAscIdAsc(PaymentStatus.PENDING,PaymentKind.REFUND)) {
            try {var p=row.getFolio().getReservation().getProperty();refunds.process(p.getCompany().getId(),p.getId(),row.getOriginalPayment().getId(),new RefundPaymentRequest(row.getAmount().abs(),row.getReason(),row.getRefundRequestId(),row.getCashShift()==null?null:row.getCashShift().getId()),"payment-automation");}catch(Exception ignored){ /* Persisted PENDING remains visible and retryable. */ }
        }
        for(PmsPaymentSettings config:settings.findAllByAutomaticDepositLinksTrue()) {try{scanDeposits(config.getPropertyId());}catch(Exception ignored){ /* Other hotels continue; persistent status remains inspectable. */ }}
    }
    void reconcile(Long id){
        var scope=tx.execute(s -> {var r=requests.findById(id).orElseThrow();var p=r.getFolio().getReservation().getProperty();return new Long[]{p.getCompany().getId(),p.getId()};});
        try {checkout.reconcile(scope[0],scope[1],id,"payment-automation");}
        catch(Exception e){tx.executeWithoutResult(s -> {var row=requests.findById(id).orElseThrow();row.setAutomationError("Anbieterabgleich offen. Gespeicherten Vorgang prüfen; keine Ersatzbelastung erzeugen.");row.setLastCheckedAt(LocalDateTime.now());requests.save(row);});throw e;}
    }
    void processEvent(Long id){
        PmsStripeEvent event=tx.execute(s -> {var row=events.findLocked(id).orElseThrow();if(!List.of("PENDING","PROCESSING").contains(row.getStatus()) || row.getNextAttemptAt().isAfter(LocalDateTime.now()))return null;row.setStatus("PROCESSING");row.setAttempts(row.getAttempts()+1);row.setNextAttemptAt(LocalDateTime.now().plusMinutes(10));return events.save(row);});
        if(event==null)return;
        try {
            Long requestId=tx.execute(s -> {
                PmsPaymentRequest request=event.getObjectId().startsWith("cs_")?requests.findByProviderSessionId(event.getObjectId()).orElse(null):requests.findByProviderPaymentIntentId(event.getObjectId()).orElse(null);
                if(request==null && event.getRequestKey()!=null) request=requests.findByRequestKey(event.getRequestKey()).orElse(null);
                if(request==null)return null;
                if(!PmsStripeWebhookService.owns(request.getMerchantContext(),event.getAccountId()))throw new IllegalArgumentException("Händlerkontext stimmt nicht überein.");
                return request.getId();
            });
            if(requestId!=null) reconcile(requestId);
            else if(event.getObjectId().startsWith("re_")) {
                if(event.getRequestKey()!=null && event.getRequestKey().matches("chrono-pms-refund-[0-9]+")) {
                    Long refundId=Long.parseLong(event.getRequestKey().substring("chrono-pms-refund-".length()));
                    refunds.acceptProviderRefund(refundId,event.getObjectId(),event.getAccountId());requestId=-1L;
                } else {
                Payment refund=tx.execute(s -> {var r=payments.findByProviderTransactionId(event.getObjectId()).orElse(null);if(r!=null){r.getOriginalPayment().getId();r.getFolio().getReservation().getProperty().getCompany().getId();}return r;});
                if(refund!=null){if(!PmsStripeWebhookService.owns(refund.getMerchantContext(),event.getAccountId()))throw new IllegalArgumentException();var p=refund.getFolio().getReservation().getProperty();refunds.process(p.getCompany().getId(),p.getId(),refund.getOriginalPayment().getId(),new RefundPaymentRequest(refund.getAmount().abs(),refund.getReason(),refund.getRefundRequestId(),null),"stripe-webhook");requestId=-1L;}
                }
            }
            final boolean matched=requestId!=null;tx.executeWithoutResult(s -> {var row=events.findLocked(id).orElseThrow();row.setStatus(matched?"PROCESSED":"IGNORED");row.setLastError(null);events.save(row);});
        }catch(Exception e){tx.executeWithoutResult(s -> {var row=events.findLocked(id).orElseThrow();row.setStatus(e instanceof IllegalArgumentException?"REJECTED":"PENDING");row.setLastError(e instanceof IllegalArgumentException?"Händlerzuordnung abgelehnt.":"Abgleich noch nicht möglich; gespeicherter Vorgang bleibt unverändert.");row.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60,1L<<Math.min(6,row.getAttempts()))));events.save(row);});}
    }
    void scanDeposits(Long propertyId){
        var candidates=tx.execute(s -> {var c=settings.findById(propertyId).orElseThrow();return reservations.findDepositCandidates(propertyId,c.getDepositCursor(),org.springframework.data.domain.PageRequest.of(0,50));});
        String error=null;
        for(Long id:candidates){try {queueDeposit(propertyId,id);}catch(Exception e){error="Anzahlungslink für Reservierung "+id+" konnte nicht bereitgestellt werden. Zahlungs-/Versandeinstellungen und offenen Betrag prüfen.";}}
        final String result=error;tx.executeWithoutResult(s -> {var c=settings.findById(propertyId).orElseThrow();c.setDepositCursor(candidates.isEmpty()?0:candidates.get(candidates.size()-1));c.setLastAutomationAt(LocalDateTime.now());c.setLastAutomationError(result);settings.save(c);});
    }
    private record Deposit(Long companyId,Long folioId,java.math.BigDecimal amount,String key){}
    void queueDeposit(Long propertyId,Long reservationId){
        Deposit deposit=tx.execute(s -> {
            var config=settings.findById(propertyId).orElseThrow();if(!config.isAutomaticDepositLinks() || config.getVerifiedAt()==null)return null;
            var r=reservations.findById(reservationId).orElseThrow();if(!r.getProperty().getId().equals(propertyId)||r.getStatus()!=ReservationStatus.CONFIRMED)return null;
            var policy=policies.view(r);if(!policy.depositOverdue() || policy.depositOutstandingAmount().signum()<=0)return null;
            var folio=folios.findAllByReservation_IdOrderByIdAsc(reservationId).stream().filter(f -> !f.isGroupMaster() && f.getStatus()==FolioStatus.OPEN).findFirst().orElseThrow();
            return new Deposit(r.getProperty().getCompany().getId(),folio.getId(),policy.depositOutstandingAmount(),"deposit-reservation-"+reservationId);
        });
        if(deposit==null)return;
        Long existing=requests.findByRequestKey(deposit.key()).map(PmsPaymentRequest::getId).orElse(null);
        PmsPaymentRequestView request=existing==null?checkout.createAutomaticDeposit(deposit.companyId(),propertyId,deposit.folioId(),reservationId,new CreatePmsPaymentRequest(deposit.key(),deposit.amount(),"PAYMENT")):
                checkout.reconcile(deposit.companyId(),propertyId,existing,"deposit-automation");
        if("OPEN".equals(request.status())&&request.checkoutUrl()!=null)delivery.queuePaymentLink(deposit.companyId(),propertyId,reservationId,deposit.key(),request.checkoutUrl(),request.amount(),request.currencyCode());
    }
}
