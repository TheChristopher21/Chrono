package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@Service
public class PmsLegacyMerchantService {
    private final HotelPropertyRepository properties;private final PaymentRepository payments;private final PmsPaymentRequestRepository requests;
    private final List<PmsPaymentGateway> gateways;private final TransactionTemplate tx;private final PmsAuditWriter audit;
    public PmsLegacyMerchantService(HotelPropertyRepository properties,PaymentRepository payments,PmsPaymentRequestRepository requests,List<PmsPaymentGateway> gateways,PlatformTransactionManager transactions,PmsAuditWriter audit){this.properties=properties;this.payments=payments;this.requests=requests;this.gateways=gateways;this.tx=new TransactionTemplate(transactions);this.audit=audit;}
    public record Bind(String target,Long id,String merchantContext){}
    public void bind(Long companyId,Long propertyId,Bind input,String actor){
        if(input.id()==null || !List.of("PAYMENT","REQUEST").contains(input.target()) || input.merchantContext()==null || !input.merchantContext().matches("(PLATFORM|CONNECT):acct_[A-Za-z0-9]+"))throw error(HttpStatus.BAD_REQUEST,"Vorgang und Händlerkonto angeben.");
        var gateway=gateways.stream().filter(g -> g instanceof StripePmsPaymentGateway).findFirst().orElseThrow(() -> error(HttpStatus.CONFLICT,"Stripe ist nicht eingerichtet."));
        Object target=tx.execute(s -> {
            require(companyId,propertyId);
            if("PAYMENT".equals(input.target())){var row=payments.findByIdForUpdate(input.id(),propertyId,companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Zahlung nicht gefunden."));if(row.getKind()!=PaymentKind.PAYMENT || row.getMethod()!=PaymentMethod.CARD)throw error(HttpStatus.CONFLICT,"Nur ursprüngliche Kartenzahlungen zuordnen.");unchanged(row.getMerchantContext(),input.merchantContext());row.getFolio().getReservation().getProperty().getCurrencyCode();return row;}
            var row=requests.findById(input.id()).filter(r -> r.getFolio().getReservation().getProperty().getId().equals(propertyId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Zahlungsauftrag nicht gefunden."));unchanged(row.getMerchantContext(),input.merchantContext());row.getFolio().getId();return row;
        });
        try {
            gateway.verifyMerchant(input.merchantContext());
            if(target instanceof Payment payment)gateway.verifyCapturedPayment(payment.getFolio().getReservation().getProperty(),payment.getFolio(),payment.getAmount(),payment.getProviderTransactionId()==null?payment.getReference():payment.getProviderTransactionId(),input.merchantContext());
            else {var row=(PmsPaymentRequest)target;if(row.getProviderSessionId()==null)throw error(HttpStatus.CONFLICT,"Historische Checkout-ID fehlt. Zuerst beim Anbieter klären.");var result=gateway.inspectCheckout(row.getProviderSessionId(),input.merchantContext());if(!propertyId.toString().equals(result.propertyId()) || !row.getFolio().getId().toString().equals(result.folioId()) || !row.getCurrencyCode().equalsIgnoreCase(result.currencyCode()))throw error(HttpStatus.CONFLICT,"Anbieterauftrag gehört nicht zu diesem Hotel/Gastkonto.");}
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw error(HttpStatus.BAD_GATEWAY,"Historischer Vorgang konnte bei diesem Händler nicht bestätigt werden.");}
        tx.executeWithoutResult(s -> {
            var property=require(companyId,propertyId);
            if(target instanceof Payment){var row=payments.findByIdForUpdate(input.id(),propertyId,companyId).orElseThrow();unchanged(row.getMerchantContext(),input.merchantContext());row.setMerchantContext(input.merchantContext());payments.save(row);for(var state:PaymentStatus.values())for(var refund:payments.findAllByOriginalPayment_IdAndStatus(row.getId(),state)){unchanged(refund.getMerchantContext(),input.merchantContext());refund.setMerchantContext(input.merchantContext());payments.save(refund);}}
            else {var row=requests.findById(input.id()).orElseThrow();unchanged(row.getMerchantContext(),input.merchantContext());row.setMerchantContext(input.merchantContext());requests.save(row);}
            audit.append(property,"payment.legacy_merchant_verified",input.target().toLowerCase(java.util.Locale.ROOT),input.id().toString(),"{}");
        });
    }
    private HotelProperty require(Long companyId,Long propertyId){return properties.findByIdAndCompany_IdForUpdate(propertyId,companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));}
    private void unchanged(String existing,String next){if(existing!=null&&!existing.equals(next))throw error(HttpStatus.CONFLICT,"Der gespeicherte Händlerkontext ist unveränderlich.");}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
