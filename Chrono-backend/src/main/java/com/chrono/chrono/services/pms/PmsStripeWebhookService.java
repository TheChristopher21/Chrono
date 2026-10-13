package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.PmsStripeEvent;
import com.chrono.chrono.repositories.pms.PmsStripeEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
@Service
public class PmsStripeWebhookService {
    private final PmsStripeEventRepository events;private final ObjectMapper mapper;private final String secret;private final TransactionTemplate tx;
    public PmsStripeWebhookService(PmsStripeEventRepository events,ObjectMapper mapper,@Value("${app.pms.payments.stripe.webhook-secret:}") String secret,PlatformTransactionManager transactions){this.events=events;this.mapper=mapper;this.secret=secret;this.tx=new TransactionTemplate(transactions);}
    public void accept(String raw,String signature) {
        if(secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Stripe-Webhook ist nicht eingerichtet.");
        if(raw==null || raw.length()>262144 || signature==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Stripe-Zustellung.");
        try { com.stripe.net.Webhook.Signature.verifyHeader(raw,signature,secret,300); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Stripe-Signatur.");}
        final PmsStripeEvent row=new PmsStripeEvent();
        try {
            var event=mapper.readTree(raw);var object=event.path("data").path("object");
            row.setEventId(event.path("id").asText());row.setEventType(event.path("type").asText());row.setObjectId(object.path("id").asText());
            row.setAccountId(event.path("account").isTextual()?event.path("account").asText():null);
            String key=object.path("metadata").path("chronoRequestId").asText(null);
            if(key==null)key=object.path("metadata").path("chronoRefundRequest").asText(null);row.setRequestKey(key);
            if(!row.getEventId().matches("evt_[A-Za-z0-9]+") || row.getEventType().length()>100 || row.getObjectId().isBlank() || row.getObjectId().length()>180 || key!=null && key.length()>80) throw new IllegalArgumentException();
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiges Stripe-Ereignis.");}
        row.setReceivedAt(LocalDateTime.now());row.setNextAttemptAt(row.getReceivedAt());
        try {tx.executeWithoutResult(status -> {if(!events.existsByEventId(row.getEventId())) events.saveAndFlush(row);});}
        catch(org.springframework.dao.DataIntegrityViolationException e){if(!events.existsByEventId(row.getEventId()))throw e;}
    }
    static boolean owns(String context,String account){return context!=null && (context.startsWith("CONNECT:") ? context.equals("CONNECT:"+account) : context.startsWith("PLATFORM:") && account==null);}
}
