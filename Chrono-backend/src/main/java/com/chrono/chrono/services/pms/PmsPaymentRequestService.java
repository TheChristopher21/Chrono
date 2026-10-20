package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.beans.factory.annotation.Value;
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

/** Provider calls follow a committed intent. A browser return never posts a payment. */
@Service
public class PmsPaymentRequestService {
    private final PmsPaymentRequestRepository requests;
    private final FolioRepository folios;
    private final FolioItemRepository items;
    private final PaymentRepository payments;
    private final HotelPropertyRepository properties;
    private final PmsFinancialPeriodService periods;
    private final PmsAuditWriter audit;
    private final List<PmsPaymentGateway> gateways;
    private final TransactionTemplate tx;
    private final String returnUrl;
    @org.springframework.beans.factory.annotation.Autowired private PmsPaymentSettingsRepository automationSettings;
    @org.springframework.beans.factory.annotation.Autowired private PmsReservationPolicyService reservationPolicies;

    public PmsPaymentRequestService(PmsPaymentRequestRepository requests, FolioRepository folios,
            FolioItemRepository items, PaymentRepository payments, HotelPropertyRepository properties,
            PmsFinancialPeriodService periods, PmsAuditWriter audit, List<PmsPaymentGateway> gateways,
            PlatformTransactionManager transactions, @Value("${app.public-base-url:https://chrono-logisch.ch}") String publicBaseUrl) {
        this.requests = requests; this.folios = folios; this.items = items; this.payments = payments;
        this.properties = properties; this.periods = periods; this.audit = audit; this.gateways = gateways;
        this.tx = new TransactionTemplate(transactions);
        this.returnUrl = publicBaseUrl.replaceAll("/+$", "") + "/pms-payment-return";
    }

    public List<PmsPaymentRequestView> list(Long companyId, Long propertyId, Long folioId) {
        return tx.execute(status -> { requireFolio(companyId, propertyId, folioId);
            return requests.findAllByFolio_IdOrderByCreatedAtDesc(folioId).stream().map(this::view).toList(); });
    }

    public PmsPaymentRequestView create(Long companyId, Long propertyId, Long folioId, CreatePmsPaymentRequest input, String username) {
        return create(companyId,propertyId,folioId,input,username,null);
    }
    public PmsPaymentRequestView createAutomaticDeposit(Long companyId,Long propertyId,Long folioId,Long reservationId,CreatePmsPaymentRequest input){
        return create(companyId,propertyId,folioId,input,"deposit-automation",reservationId);
    }
    private PmsPaymentRequestView create(Long companyId, Long propertyId, Long folioId, CreatePmsPaymentRequest input, String username,Long automaticReservationId) {
        if (input.requestId() == null || !input.requestId().matches("[A-Za-z0-9._:-]{8,80}")
                || !List.of("PAYMENT", "AUTHORIZATION").contains(input.kind())) throw error(HttpStatus.BAD_REQUEST, "Ungültiger Zahlungsvorgang.");
        Long id = tx.execute(status -> {
            HotelProperty property = lock(companyId, propertyId);
            BigDecimal amount = amount(input.amount(), property.getCurrencyCode());
            gateway().validateAmount(amount, property.getCurrencyCode());
            Folio folio = requireFolio(companyId, propertyId, folioId);
            if(automaticReservationId!=null){
                var settings=automationSettings.findById(propertyId).orElseThrow(() -> error(HttpStatus.CONFLICT,"Anzahlungsautomatik nicht freigegeben."));
                var reservation=folio.getReservation();var policy=reservationPolicies.view(reservation);
                if(!settings.isAutomaticDepositLinks() || settings.getVerifiedAt()==null || !automaticReservationId.equals(reservation.getId()) || reservation.getStatus()!=ReservationStatus.CONFIRMED
                        || !policy.depositOverdue() || policy.depositOutstandingAmount().compareTo(amount)!=0 || !"PAYMENT".equals(input.kind()))throw error(HttpStatus.CONFLICT,"Anzahlungsauftrag entspricht nicht mehr den aktuellen Bedingungen.");
            }
            PmsPaymentRequest existing = requests.findByRequestKey(input.requestId()).orElse(null);
            if (existing != null) {
                if (!existing.getFolio().getId().equals(folioId) || !existing.getKind().equals(input.kind()) || existing.getAmount().compareTo(amount) != 0)
                    throw error(HttpStatus.CONFLICT, "Die Vorgangs-ID gehört zu einer anderen Zahlungsanforderung.");
                return existing.getId();
            }
            if (folio.getStatus() != FolioStatus.OPEN) throw error(HttpStatus.CONFLICT, "Das Gastkonto ist geschlossen.");
            periods.assertPostingOpen(property, periods.currentBusinessDate(property));
            if ("PAYMENT".equals(input.kind())) {
                BigDecimal reserved = requests.findAllByFolio_IdOrderByCreatedAtDesc(folioId).stream()
                        .filter(r -> "PAYMENT".equals(r.getKind()) && !terminal(r.getStatus())).map(PmsPaymentRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (amount.add(reserved).compareTo(balance(folioId)) > 0) throw error(HttpStatus.CONFLICT, "Der Betrag überschreitet das offene Guthaben unter Berücksichtigung offener Zahlungslinks.");
            }
            PmsPaymentRequest row = new PmsPaymentRequest(); row.setFolio(folio); row.setAmount(amount);
            row.setCurrencyCode(property.getCurrencyCode()); row.setKind(input.kind()); row.setRequestKey(input.requestId()); row.setCreatedBy(username);
            row.setMerchantContext(gateway().merchantContext(property));
            requests.saveAndFlush(row);
            audit.append(property, "payment_request.created", "payment_request", row.getId().toString(), "{}");
            return row.getId();
        });
        return reconcile(companyId, propertyId, id, username);
    }

    public PmsPaymentRequestView capture(Long companyId, Long propertyId, Long id, BigDecimal input, String username) {
        tx.executeWithoutResult(status -> {
            lock(companyId, propertyId); PmsPaymentRequest row = requireRequest(companyId, propertyId, id);
            BigDecimal amount = input == null ? row.getAmount() : amount(input, row.getCurrencyCode());
            gateway().validateAmount(amount, row.getCurrencyCode());
            if ("CAPTURE".equals(row.getActionRequested())) {
                if (row.getCaptureAmount().compareTo(amount) != 0) throw error(HttpStatus.CONFLICT, "Der bereits gestartete Einzug hat einen anderen Betrag.");
                return;
            }
            if (!"AUTHORIZATION".equals(row.getKind()) || !"AUTHORIZED".equals(row.getStatus())) throw error(HttpStatus.CONFLICT, "Es liegt keine bestätigte Kartengarantie vor.");
            if (amount.compareTo(row.getAmount()) > 0 || amount.compareTo(balance(row.getFolio().getId())) > 0) throw error(HttpStatus.CONFLICT, "Der Einzug überschreitet die Garantie oder den offenen Gastkontobetrag.");
            row.setCaptureAmount(amount); row.setActionRequested("CAPTURE"); row.setStatus("CAPTURE_REQUESTED"); requests.save(row);
        });
        return reconcile(companyId, propertyId, id, username);
    }

    public PmsPaymentRequestView release(Long companyId, Long propertyId, Long id, String username) {
        tx.executeWithoutResult(status -> {
            lock(companyId, propertyId); PmsPaymentRequest row = requireRequest(companyId, propertyId, id);
            if ("RELEASE".equals(row.getActionRequested()) || "RELEASED".equals(row.getStatus()) || "EXPIRED".equals(row.getStatus())) return;
            if (terminal(row.getStatus()) || row.getActionRequested() != null) throw error(HttpStatus.CONFLICT, "Dieser Zahlungsvorgang kann nicht mehr freigegeben werden.");
            row.setActionRequested("RELEASE"); row.setStatus("RELEASE_REQUESTED"); requests.save(row);
        });
        return reconcile(companyId, propertyId, id, username);
    }

    public PmsPaymentRequestView reconcile(Long companyId, Long propertyId, Long id, String username) {
        PmsPaymentRequest row = tx.execute(status -> {
            lock(companyId, propertyId); PmsPaymentRequest saved = requireRequest(companyId, propertyId, id);
            saved.getFolio().getReservation().getProperty().getCurrencyCode(); return saved;
        });
        if (terminal(row.getStatus())) return view(row);
        PmsPaymentGateway gateway = gateway();
        try {
            PmsPaymentGateway.CheckoutResult result;
            if (row.getProviderSessionId() == null) {
                if (row.getCreatedAt().isBefore(LocalDateTime.now().minusHours(23))) throw error(HttpStatus.CONFLICT, "Unbekannten Anbieterstatus vor einem erneuten Geldfluss manuell abgleichen.");
                result = gateway.createCheckout(row.getFolio().getReservation().getProperty(), row.getFolio(), row.getAmount(),
                        "AUTHORIZATION".equals(row.getKind()), returnUrl, row.getRequestKey(), row.getMerchantContext());
                apply(companyId, propertyId, id, result, username);
                row = tx.execute(status -> requireRequest(companyId, propertyId, id));
            } else result = gateway.inspectCheckout(row.getProviderSessionId(), row.getMerchantContext());
            // Verify ownership, amount and status before any capture/release against the retrieved intent.
            apply(companyId, propertyId, id, result, username);
            // Inspect before retrying a previously interrupted action: never issue another capture after success.
            if (!"succeeded".equals(result.status()) && !List.of("canceled", "expired").contains(result.status())) {
                if ("CAPTURE".equals(row.getActionRequested()) && "requires_capture".equals(result.status())) {
                    gateway.captureAuthorization(result.paymentIntentId(), row.getCaptureAmount(), row.getCurrencyCode(), row.getRequestKey(), row.getMerchantContext());
                    result = gateway.inspectCheckout(row.getProviderSessionId(), row.getMerchantContext());
                } else if ("RELEASE".equals(row.getActionRequested())) {
                    gateway.releaseAuthorization(row.getProviderSessionId(), result.paymentIntentId(), row.getRequestKey(), row.getMerchantContext());
                    result = gateway.inspectCheckout(row.getProviderSessionId(), row.getMerchantContext());
                }
            }
            return apply(companyId, propertyId, id, result, username);
        } catch (ResponseStatusException error) { throw error; }
        catch (com.stripe.exception.InvalidRequestException rejection) {
            if (row.getProviderSessionId() == null) tx.executeWithoutResult(status -> {
                lock(companyId, propertyId); PmsPaymentRequest saved = requireRequest(companyId, propertyId, id);
                saved.setStatus("FAILED"); saved.setProviderStatus("provider_rejected"); requests.save(saved);
            });
            throw error(HttpStatus.BAD_GATEWAY, "Der Zahlungsanbieter hat die Anfrage abgewiesen. Währung und Anbietereinstellungen prüfen.");
        }
        catch (Exception error) { throw error(HttpStatus.BAD_GATEWAY, "Anbieterstatus unklar. Der Vorgang ist gespeichert und kann erneut abgeglichen werden."); }
    }

    private PmsPaymentRequestView apply(Long companyId, Long propertyId, Long id, PmsPaymentGateway.CheckoutResult result, String username) {
        return tx.execute(status -> {
            HotelProperty property = lock(companyId, propertyId); PmsPaymentRequest saved = requireRequest(companyId, propertyId, id);
            if (terminal(saved.getStatus())) return view(saved);
            if (result == null || result.sessionId() == null || result.status() == null
                    || !propertyId.toString().equals(result.propertyId()) || !saved.getFolio().getId().toString().equals(result.folioId())
                    || !saved.getCurrencyCode().equalsIgnoreCase(result.currencyCode())
                    || saved.getProviderSessionId() != null && !saved.getProviderSessionId().equals(result.sessionId()))
                throw error(HttpStatus.BAD_GATEWAY, "Der Anbieter konnte diesen Zahlungsvorgang nicht eindeutig bestätigen.");
            saved.setProviderSessionId(result.sessionId()); saved.setProviderPaymentIntentId(result.paymentIntentId());
            if (result.checkoutUrl() != null) saved.setCheckoutUrl(result.checkoutUrl()); saved.setProviderStatus(result.status());
            switch (result.status()) {
                case "succeeded" -> {
                    BigDecimal expected = saved.getCaptureAmount() == null ? saved.getAmount() : saved.getCaptureAmount();
                    if (result.paymentIntentId() == null || result.receivedAmount() == null || expected.compareTo(result.receivedAmount()) != 0)
                        throw error(HttpStatus.BAD_GATEWAY, "Der bestätigte Zahlungsbetrag weicht vom Vorgang ab; manuell abgleichen.");
                    Payment posted = payments.findByProviderTransactionId(result.paymentIntentId()).orElse(null);
                    if (posted != null && (!posted.getFolio().getId().equals(saved.getFolio().getId()) || posted.getAmount().compareTo(expected) != 0 || !java.util.Objects.equals(posted.getMerchantContext(),saved.getMerchantContext())))
                        throw error(HttpStatus.CONFLICT, "Die Anbieterzahlung wurde bereits einem anderen Gastkonto zugeordnet.");
                    if (posted == null) {
                        periods.assertPostingOpen(property, periods.currentBusinessDate(property));
                        Payment payment = new Payment(); payment.setFolio(saved.getFolio()); payment.setAmount(expected); payment.setMethod(PaymentMethod.CARD);
                        payment.setProviderTransactionId(result.paymentIntentId()); payment.setReference(result.paymentIntentId()); payment.setProviderStatus("succeeded");
                        payment.setMerchantContext(saved.getMerchantContext());
                        payment.setCreatedBy(username); payment.setPostingDate(periods.currentBusinessDate(property)); payments.save(payment);
                    }
                    saved.setStatus("PAID");
                }
                case "requires_capture" -> {
                    if (result.capturableAmount() == null || result.capturableAmount().compareTo(saved.getAmount()) != 0 || !"AUTHORIZATION".equals(saved.getKind()))
                        throw error(HttpStatus.BAD_GATEWAY, "Der Garantieumfang entspricht nicht dem Vorgang.");
                    saved.setStatus(saved.getActionRequested() == null ? "AUTHORIZED" : saved.getActionRequested() + "_REQUESTED");
                    if (saved.getFolio().getReservation().getGuaranteeStatus() == ReservationGuaranteeStatus.UNGUARANTEED)
                        saved.getFolio().getReservation().setGuaranteeStatus(ReservationGuaranteeStatus.CREDIT_CARD);
                }
                case "canceled" -> saved.setStatus("RELEASED");
                case "expired" -> saved.setStatus("EXPIRED");
                default -> saved.setStatus(saved.getActionRequested() == null ? "OPEN" : saved.getActionRequested() + "_REQUESTED");
            }
            requests.save(saved);
            saved.setLastCheckedAt(java.time.LocalDateTime.now()); saved.setAutomationError(null);
            if (List.of("RELEASED", "EXPIRED").contains(saved.getStatus())
                    && saved.getFolio().getReservation().getGuaranteeStatus() == ReservationGuaranteeStatus.CREDIT_CARD) {
                boolean otherGuarantee = folios.findAllByReservation_IdOrderByIdAsc(saved.getFolio().getReservation().getId()).stream()
                        .flatMap(f -> requests.findAllByFolio_IdOrderByCreatedAtDesc(f.getId()).stream())
                        .anyMatch(r -> !r.getId().equals(saved.getId()) && "AUTHORIZED".equals(r.getStatus()));
                if (!otherGuarantee) saved.getFolio().getReservation().setGuaranteeStatus(ReservationGuaranteeStatus.UNGUARANTEED);
            }
            audit.append(property, "payment_request.reconciled", "payment_request", id.toString(), "{\"status\":\"" + saved.getStatus() + "\"}");
            return view(saved);
        });
    }

    private boolean terminal(String value) { return List.of("PAID", "RELEASED", "EXPIRED", "FAILED").contains(value); }
    private PmsPaymentGateway gateway() { return gateways.stream().filter(g -> g.supports(PaymentMethod.CARD)).findFirst()
            .orElseThrow(() -> error(HttpStatus.CONFLICT, "Kein Kartenanbieter eingerichtet.")); }
    private BigDecimal amount(BigDecimal input, String currencyCode) {
        if (input == null || input.signum() <= 0) throw error(HttpStatus.BAD_REQUEST, "Ein positiver Betrag ist erforderlich.");
        return PmsMoney.require(input, currencyCode);
    }
    private BigDecimal balance(Long id) {
        return items.findAllByFolio_IdOrderByServiceDateAscIdAsc(id).stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(payments.findAllByFolio_IdOrderByReceivedAtAsc(id).stream().filter(p -> p.getStatus() == PaymentStatus.POSTED).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private HotelProperty lock(Long companyId, Long propertyId) { return properties.findByIdAndCompany_IdForUpdate(propertyId, companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Hotel nicht gefunden.")); }
    private Folio requireFolio(Long companyId, Long propertyId, Long id) {
        return folios.findByIdAndReservation_Property_Company_Id(id, companyId).filter(f -> f.getReservation().getProperty().getId().equals(propertyId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Gastkonto nicht gefunden."));
    }
    private PmsPaymentRequest requireRequest(Long companyId, Long propertyId, Long id) {
        return requests.findById(id).filter(r -> r.getFolio().getReservation().getProperty().getId().equals(propertyId)
                && r.getFolio().getReservation().getProperty().getCompany().getId().equals(companyId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Zahlungsvorgang nicht gefunden."));
    }
    private PmsPaymentRequestView view(PmsPaymentRequest r) { return new PmsPaymentRequestView(r.getId(), r.getRequestKey(), r.getFolio().getId(), r.getKind(), r.getStatus(), r.getAmount(), r.getCaptureAmount(), r.getCurrencyCode(), r.getCheckoutUrl(), r.getProviderSessionId(), r.getProviderPaymentIntentId(), r.getProviderStatus(), r.getCreatedAt(), r.getUpdatedAt()); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
