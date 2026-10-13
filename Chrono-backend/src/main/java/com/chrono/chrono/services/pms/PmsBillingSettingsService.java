package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsDeliveryDtos.Settings;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Set;
import jakarta.mail.internet.InternetAddress;

@Service @Transactional
public class PmsBillingSettingsService {
    private final PmsBillingSettingsRepository settings;
    private final HotelPropertyRepository properties;
    private final PmsAuditWriter audit;
    public PmsBillingSettingsService(PmsBillingSettingsRepository settings,HotelPropertyRepository properties,PmsAuditWriter audit) {
        this.settings=settings;this.properties=properties;this.audit=audit;
    }
    @Transactional(readOnly=true)
    public PmsBillingSettings effective(HotelProperty property) {
        return settings.findById(property.getId()).orElseGet(()->{PmsBillingSettings s=new PmsBillingSettings();s.setProperty(property);return s;});
    }
    @Transactional(readOnly=true)
    public Settings view(HotelProperty property) {return view(effective(property));}
    public Settings save(HotelProperty property,Settings input,String actor) {
        properties.findByIdAndCompany_IdForUpdate(property.getId(),property.getCompany().getId()).orElseThrow();
        PmsBillingSettings s=effective(property);
        if(s.getVersion()!=input.version())throw error("Die Versandeinstellungen wurden geändert. Bitte neu laden.");
        if(input.invoiceLanguage()==null||!Set.of("DE","EN","FR","IT","ES").contains(input.invoiceLanguage()))throw error("Nicht unterstützte Rechnungssprache.");
        if(input.invoiceSubject()==null||input.invoiceSubject().isBlank()||input.invoiceSubject().length()>240||input.reminderSubject()==null||input.reminderSubject().isBlank()||input.reminderSubject().length()>240
            ||input.invoiceBody()==null||input.invoiceBody().isBlank()||input.invoiceBody().length()>8000||input.reminderBody()==null||input.reminderBody().isBlank()||input.reminderBody().length()>8000)throw error("Gültige Betreff- und Textvorlagen sind erforderlich.");
        if(input.firstReminderDays()<1||input.firstReminderDays()>365||input.reminderIntervalDays()<1||input.reminderIntervalDays()>365||input.maxReminders()<1||input.maxReminders()>12)throw error("Ungültige Mahnfristen.");
        if(input.mailEnabled())email(input.senderEmail());
        if(input.replyTo()!=null&&!input.replyTo().isBlank())email(input.replyTo());
        if((input.automaticInvoices()||input.sendReminders())&&!input.mailEnabled())throw error("Automatischer Versand benötigt einen aktivierten Hotelabsender.");
        header(input.senderName());header(input.invoiceSubject());header(input.reminderSubject());
        s.setMailEnabled(input.mailEnabled());s.setSenderEmail(trim(input.senderEmail()));s.setSenderName(trim(input.senderName()));s.setReplyTo(trim(input.replyTo()));
        s.setAutomaticInvoices(input.automaticInvoices());s.setInvoiceLanguage(input.invoiceLanguage());
        s.setAutomaticReminders(input.automaticReminders());s.setSendReminders(input.sendReminders());
        s.setFirstReminderDays(input.firstReminderDays());s.setReminderIntervalDays(input.reminderIntervalDays());s.setMaxReminders(input.maxReminders());
        s.setInvoiceSubject(input.invoiceSubject());s.setInvoiceBody(input.invoiceBody());s.setReminderSubject(input.reminderSubject());s.setReminderBody(input.reminderBody());
        s.setUpdatedAt(LocalDateTime.now());s.setUpdatedBy(actor);settings.saveAndFlush(s);
        audit.append(property,"billing.settings_changed","property",property.getId().toString(),"{\"mailEnabled\":"+s.isMailEnabled()+",\"automaticInvoices\":"+s.isAutomaticInvoices()+"}");
        return view(s);
    }
    private Settings view(PmsBillingSettings s) {return new Settings(s.getVersion(),s.isMailEnabled(),s.getSenderEmail(),s.getSenderName(),s.getReplyTo(),s.isAutomaticInvoices(),s.getInvoiceLanguage(),s.isAutomaticReminders(),s.isSendReminders(),s.getFirstReminderDays(),s.getReminderIntervalDays(),s.getMaxReminders(),s.getInvoiceSubject(),s.getInvoiceBody(),s.getReminderSubject(),s.getReminderBody());}
    public static String email(String value) {
        try {String clean=trim(value);header(clean);if(clean==null||clean.length()>190)throw new IllegalArgumentException();
            InternetAddress[] parsed=InternetAddress.parse(clean,true);if(parsed.length!=1||!clean.equals(parsed[0].getAddress())||!clean.contains("@"))throw new IllegalArgumentException();parsed[0].validate();return clean;
        }catch(Exception invalid){throw error("Eine einzelne gültige E-Mail-Adresse ist erforderlich.");}
    }
    public static void header(String value){if(value!=null&&(value.contains("\r")||value.contains("\n")))throw error("Zeilenumbrüche sind in E-Mail-Kopfdaten nicht zulässig.");}
    private static String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private static ResponseStatusException error(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
