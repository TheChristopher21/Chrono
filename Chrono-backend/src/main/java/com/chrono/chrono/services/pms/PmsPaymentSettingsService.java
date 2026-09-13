package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class PmsPaymentSettingsService {
    private final HotelPropertyRepository properties;
    private final PmsPaymentSettingsRepository settings;
    private final List<PmsPaymentGateway> gateways;
    private final TransactionTemplate tx;
    private final PmsAuditWriter audit;
    public PmsPaymentSettingsService(HotelPropertyRepository properties,PmsPaymentSettingsRepository settings,List<PmsPaymentGateway> gateways,PlatformTransactionManager transactions,PmsAuditWriter audit) {
        this.properties=properties;this.settings=settings;this.gateways=gateways;this.tx=new TransactionTemplate(transactions);this.audit=audit;
    }
    public record Settings(String merchantContext,boolean automaticDepositLinks,Long version,LocalDateTime verifiedAt) {}
    public Settings get(Long companyId,Long propertyId) {
        return tx.execute(status -> { require(companyId,propertyId);return view(settings.findById(propertyId).orElse(new PmsPaymentSettings())); });
    }
    public Settings update(Long companyId,Long propertyId,Settings input,String actor) {
        Settings current=get(companyId,propertyId);
        String context=input.merchantContext()==null?null:input.merchantContext().trim();
        if(context==null || !context.matches("(PLATFORM|CONNECT):acct_[A-Za-z0-9]+")) throw error(HttpStatus.BAD_REQUEST,"Händlerkonto als PLATFORM:acct_… oder CONNECT:acct_… angeben.");
        boolean disablingOnly=!input.automaticDepositLinks() && java.util.Objects.equals(current.merchantContext(),context) && current.verifiedAt()!=null;
        try { if(!disablingOnly)gateways.stream().filter(g -> g instanceof StripePmsPaymentGateway).findFirst().orElseThrow(() -> error(HttpStatus.CONFLICT,"Stripe ist auf diesem Server noch nicht eingerichtet.")).verifyMerchant(context); }
        catch(ResponseStatusException e){throw e;} catch(Exception e){throw error(HttpStatus.BAD_GATEWAY,"Händlerkonto konnte nicht bestätigt werden. Konto und Stripe-Konfiguration prüfen.");}
        return tx.execute(status -> {
            HotelProperty property=require(companyId,propertyId);
            PmsPaymentSettings row=settings.findById(propertyId).orElseGet(PmsPaymentSettings::new);
            if(input.version()==null || input.version()!=row.getVersion()) throw error(HttpStatus.CONFLICT,"Zahlungseinstellungen wurden zwischenzeitlich geändert. Neu laden.");
            row.setPropertyId(propertyId);row.setMerchantContext(context);row.setAutomaticDepositLinks(input.automaticDepositLinks());if(!disablingOnly)row.setVerifiedAt(LocalDateTime.now());settings.saveAndFlush(row);
            audit.append(property,"payment.settings_updated","property",propertyId.toString(),"{\"automaticDepositLinks\":"+input.automaticDepositLinks()+"}");return view(row);
        });
    }
    private HotelProperty require(Long companyId,Long propertyId){return properties.findByIdAndCompany_IdForUpdate(propertyId,companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));}
    private Settings view(PmsPaymentSettings row){return new Settings(row.getMerchantContext(),row.isAutomaticDepositLinks(),row.getVersion(),row.getVerifiedAt());}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
