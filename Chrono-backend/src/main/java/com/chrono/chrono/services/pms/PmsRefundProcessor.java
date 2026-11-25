package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.RefundPaymentRequest;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** The HTTP caller is non-transactional: persist the intent before any provider call. */
@Service
public class PmsRefundProcessor {
    private final PaymentRepository payments;
    private final FolioItemRepository items;
    private final HotelPropertyRepository properties;
    private final PmsCashService cash;
    private final PmsFinancialPeriodService periods;
    private final List<PmsPaymentGateway> gateways;
    private final TransactionTemplate tx;
    private final PmsAuditWriter audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private PmsApprovalService approvals;
    public PmsRefundProcessor(PaymentRepository payments, FolioItemRepository items, HotelPropertyRepository properties,
                              PmsCashService cash, PmsFinancialPeriodService periods, List<PmsPaymentGateway> gateways,
                              PlatformTransactionManager transactions, PmsAuditWriter audit) {
        this.payments = payments; this.items = items; this.properties = properties; this.cash = cash;
        this.periods = periods; this.gateways = gateways; this.tx = new TransactionTemplate(transactions);
        this.audit = audit;
    }

    public void process(Long companyId, Long propertyId, Long paymentId, RefundPaymentRequest request, String username) {
        if (request.requestId() == null || !request.requestId().matches("[A-Za-z0-9._:-]{8,80}")) {
            throw error(HttpStatus.BAD_REQUEST, "Für jede Rückerstattung ist eine eindeutige Vorgangs-ID erforderlich. Bei Wiederholung dieselbe ID verwenden.");
        }
        Payment intent = tx.execute(status -> prepare(companyId, propertyId, paymentId, request, username));
        if (intent.getStatus() != PaymentStatus.PENDING) return;
        tx.executeWithoutResult(s -> {var row=payments.findById(intent.getId()).orElseThrow();row.setProviderCheckedAt(LocalDateTime.now());payments.save(row);});
        PmsPaymentGateway.RefundResult result;
        if (intent.getMethod() != PaymentMethod.CARD) result = new PmsPaymentGateway.RefundResult(null, "succeeded");
        else {
            PmsPaymentGateway gateway = gateways.stream().filter(g -> g.supports(PaymentMethod.CARD)).findFirst()
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "Kein Kartenanbieter eingerichtet; Rückerstattung bleibt vorgemerkt."));
            try {
                if (intent.getProviderTransactionId() != null) result = gateway.retrieveRefund(intent.getProviderTransactionId(), intent.getMerchantContext());
                else {
                    if (intent.getReceivedAt().isBefore(LocalDateTime.now().minusHours(23))) {
                        throw error(HttpStatus.CONFLICT, "Der Anbieterstatus ist seit mehr als 23 Stunden unklar. Vor erneutem Geldfluss beim Anbieter abgleichen.");
                    }
                    result = gateway.refund(intent.getOriginalPayment(), intent.getAmount().abs(), intent.getReason(),
                            "chrono-pms-refund-" + intent.getId());
                }
            } catch (ResponseStatusException error) { throw error; }
            catch (Exception error) {
                throw error(HttpStatus.BAD_GATEWAY, "Anbieterstatus unklar. Die Rückerstattung ist gespeichert; mit derselben Vorgangs-ID erneut prüfen.");
            }
        }
        if (result == null || result.status() == null || intent.getMethod() == PaymentMethod.CARD && (result.providerId() == null || result.providerId().isBlank())) {
            throw error(HttpStatus.BAD_GATEWAY, "Der Anbieter lieferte keine bestätigbare Rückerstattung. Vorgang bleibt offen.");
        }
        verifyResult(intent,result);
        tx.executeWithoutResult(status -> {
            HotelProperty property = properties.findByIdAndCompany_IdForUpdate(propertyId, companyId).orElseThrow();
            Payment saved = payments.findByIdForUpdate(intent.getId(), propertyId, companyId).orElseThrow();
            if (saved.getStatus() != PaymentStatus.PENDING) return;
            saved.setProviderTransactionId(result.providerId());
            saved.setProviderStatus(result.status());
            if ("succeeded".equals(result.status())) {
                periods.assertPostingOpen(property, periods.currentBusinessDate(property));
                if (saved.getMethod() == PaymentMethod.CASH) cash.requireOpenShift(property, saved.getCashShift().getId(), username);
                saved.setStatus(PaymentStatus.POSTED);
                saved.setPostingDate(periods.currentBusinessDate(property));
            } else if (List.of("failed", "canceled").contains(result.status())) saved.setStatus(PaymentStatus.FAILED);
            payments.save(saved);
            audit.append(property, "payment.refund_status_changed", "payment", saved.getId().toString(),
                    "{\"status\":\"" + saved.getStatus().name() + "\",\"amount\":" + saved.getAmount().toPlainString() + "}");
        });
    }

    private Payment prepare(Long companyId, Long propertyId, Long paymentId, RefundPaymentRequest request, String username) {
        HotelProperty property = properties.findByIdAndCompany_IdForUpdate(propertyId, companyId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Hotel nicht gefunden."));
        Payment original = payments.findByIdForUpdate(paymentId, propertyId, companyId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Zahlung nicht gefunden."));
        if ("DIRECT_BILL".equals(original.getMethod().name())) throw error(HttpStatus.CONFLICT, "Firmenforderungen über die Debitorenkorrektur bearbeiten.");
        BigDecimal amount = PmsMoney.require(request.amount(), property.getCurrencyCode());
        if (original.getMethod() == PaymentMethod.CARD) gateways.stream().filter(g -> g.supports(PaymentMethod.CARD)).findFirst()
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Kein Kartenanbieter eingerichtet.")).validateAmount(amount, property.getCurrencyCode());
        if (amount.signum() <= 0) throw error(HttpStatus.BAD_REQUEST, "Der Erstattungsbetrag muss positiv sein.");
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        Payment existing = payments.findByRefundRequestId(request.requestId()).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getOriginalPayment().getId(), paymentId) || existing.getAmount().abs().compareTo(amount) != 0
                    || !Objects.equals(existing.getReason(), reason)
                    || request.cashShiftId() != null && (existing.getCashShift() == null || !existing.getCashShift().getId().equals(request.cashShiftId()))) {
                throw error(HttpStatus.CONFLICT, "Die Vorgangs-ID gehört zu einer anderen Rückerstattung.");
            }
            initializeForProvider(existing);
            return existing;
        }
        if (original.getStatus() != PaymentStatus.POSTED || original.getKind() != PaymentKind.PAYMENT) {
            throw error(HttpStatus.CONFLICT, "Nur gebuchte Originalzahlungen können rückerstattet werden.");
        }
        periods.assertPostingOpen(property, periods.currentBusinessDate(property));
        BigDecimal reserved = List.of(PaymentStatus.POSTED, PaymentStatus.PENDING).stream()
                .flatMap(s -> payments.findAllByOriginalPayment_IdAndStatus(paymentId, s).stream())
                .map(p -> p.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reserved.add(amount).compareTo(original.getAmount()) > 0) throw error(HttpStatus.CONFLICT, "Die Erstattung überschreitet den verfügbaren Betrag einschließlich offener Vorgänge.");
        if (original.getFolio().getStatus() == FolioStatus.CLOSED) {
            BigDecimal charges = items.findAllByFolio_IdOrderByServiceDateAscIdAsc(original.getFolio().getId()).stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = payments.findAllByFolio_IdOrderByReceivedAtAsc(original.getFolio().getId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.POSTED || p.getStatus() == PaymentStatus.PENDING)
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (paid.subtract(charges).compareTo(amount) < 0) throw error(HttpStatus.CONFLICT, "Nach Check-out zuerst die entsprechende Leistung mit Gutschrift korrigieren.");
        }
        if (approvals != null) approvals.consumeForRefund(property,paymentId,request);
        Payment refund = new Payment();
        refund.setFolio(original.getFolio()); refund.setOriginalPayment(original); refund.setAmount(amount.negate());
        refund.setMethod(original.getMethod()); refund.setStatus(PaymentStatus.PENDING); refund.setKind(PaymentKind.REFUND);
        refund.setReference(original.getReference()); refund.setReason(reason); refund.setRefundRequestId(request.requestId());
        refund.setMerchantContext(original.getMerchantContext());
        refund.setCreatedBy(username == null ? "system" : username);
        if (original.getMethod() == PaymentMethod.CASH) refund.setCashShift(cash.requireOpenShift(property, request.cashShiftId(), username));
        // No provider side effect exists for cash/manual methods: validate the shift and post in this same locked transaction.
        if (original.getMethod() != PaymentMethod.CARD) {
            refund.setStatus(PaymentStatus.POSTED); refund.setPostingDate(periods.currentBusinessDate(property)); refund.setProviderStatus("succeeded");
        }
        payments.saveAndFlush(refund);
        audit.append(property, "payment.refund_requested", "payment", refund.getId().toString(),
                "{\"originalPaymentId\":" + paymentId + ",\"amount\":" + amount.toPlainString() + "}");
        initializeForProvider(refund);
        return refund;
    }
    public void acceptProviderRefund(Long refundId,String providerId,String accountId) throws Exception {
        Payment intent=tx.execute(s -> {Payment r=payments.findById(refundId).orElseThrow();initializeForProvider(r);return r;});
        if(intent.getKind()!=PaymentKind.REFUND || intent.getMethod()!=PaymentMethod.CARD || !PmsStripeWebhookService.owns(intent.getMerchantContext(),accountId))throw new IllegalArgumentException("Unpassende Händlerzuordnung.");
        PmsPaymentGateway gateway=gateways.stream().filter(g -> g.supports(PaymentMethod.CARD)).findFirst().orElseThrow();
        PmsPaymentGateway.RefundResult result=gateway.retrieveRefund(providerId,intent.getMerchantContext());verifyResult(intent,result);
        if(result.paymentIntentId()==null || !providerId.equals(result.providerId()))throw new IllegalArgumentException("Erstattungsbesitz konnte nicht bestätigt werden.");
        var property=intent.getFolio().getReservation().getProperty();Long companyId=tx.execute(s -> properties.findById(property.getId()).orElseThrow().getCompany().getId());
        tx.executeWithoutResult(s -> {properties.findByIdAndCompany_IdForUpdate(property.getId(),companyId).orElseThrow();var row=payments.findByIdForUpdate(refundId,property.getId(),companyId).orElseThrow();if(row.getProviderTransactionId()!=null && !row.getProviderTransactionId().equals(providerId))throw new IllegalArgumentException("Andere Anbietererstattung bereits zugeordnet.");row.setProviderTransactionId(providerId);payments.save(row);});
        process(companyId,property.getId(),intent.getOriginalPayment().getId(),new RefundPaymentRequest(intent.getAmount().abs(),intent.getReason(),intent.getRefundRequestId(),null),"stripe-webhook");
    }
    private void verifyResult(Payment intent,PmsPaymentGateway.RefundResult result){
        if(result==null)throw error(HttpStatus.BAD_GATEWAY,"Anbietererstattung konnte nicht bestätigt werden.");
        if(result.paymentIntentId()!=null && (!result.paymentIntentId().equals(intent.getOriginalPayment().getReference()) || result.amount()==null || result.amount().compareTo(intent.getAmount().abs())!=0 || result.currencyCode()==null || !result.currencyCode().equalsIgnoreCase(intent.getFolio().getReservation().getProperty().getCurrencyCode())))
            throw error(HttpStatus.BAD_GATEWAY,"Anbietererstattung gehört nicht zum gespeicherten Vorgang.");
    }
    private void initializeForProvider(Payment refund) {
        refund.getOriginalPayment().getReference();
        refund.getOriginalPayment().getFolio().getReservation().getProperty().getCurrencyCode();
        if (refund.getCashShift() != null) refund.getCashShift().getId();
    }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
