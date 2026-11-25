package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Bounded history projections; payloads, document hashes and registration tokens are deliberately not selected. */
@Service
public class PmsHistoryService {
    private final EntityManager entityManager;
    private final ObjectProvider<PmsPropertyAccessService> accessProvider;
    public PmsHistoryService(EntityManager entityManager,ObjectProvider<PmsPropertyAccessService> accessProvider) { this.entityManager=entityManager;this.accessProvider=accessProvider; }
    public record Result(String section,List<Map<String,Object>> items,int page,int size,long totalElements,boolean hasNext) {}
    record Spec(Class<?> entity,String propertyPath,String permission,String order,String search,List<String> fields) {}
    private static Spec spec(Class<?> entity,String propertyPath,String permission,String order,String search,String fields) {
        return new Spec(entity,propertyPath,permission,order,search,List.of(fields.split(",")));
    }
    private static final Map<String,Spec> SECTIONS=Map.ofEntries(
        Map.entry("invoices",spec(PmsInvoice.class,"property","FINANCE","issueDate","invoiceNumber,recipientName","id,invoiceNumber,type,issueDate,dueDate,recipientName,currencyCode,netAmount,vatAmount,grossAmount,status")),
        Map.entry("night-audits",spec(NightAudit.class,"property","FINANCE","businessDate","closedBy","id,businessDate,arrivalsCount,departuresCount,inHouseCount,noShowCount,openBalance,closedBy,closedAt")),
        Map.entry("communications",spec(GuestCommunication.class,"property","GUESTS","createdAt","subject,recipient,sender","id,subject,recipient,sender,channel,direction,status,createdAt,sentAt,readAt")),
        Map.entry("outbox",spec(IntegrationOutboxEvent.class,"property","INTEGRATIONS","createdAt","eventType,aggregateId","id,eventType,aggregateType,aggregateId,status,attemptCount,createdAt,deliveredAt,nextAttemptAt,lastAttemptAt,lastError")),
        Map.entry("audit",spec(PmsAuditEvent.class,"property","INTEGRATIONS","createdAt","actor,eventType,aggregateId","id,actor,eventType,aggregateType,aggregateId,integrityHash,createdAt")),
        Map.entry("guest-registrations",spec(GuestRegistration.class,"reservation.property","GUESTS","completedAt","signatureName,city","id,status,city,countryCode,signatureName,completedAt,invitedAt")),
        Map.entry("resource-bookings",spec(ResourceBooking.class,"property","FRONT_DESK","startAt","title,organizerName","id,title,organizerName,startAt,endAt,attendees,status,totalAmount,createdBy,createdAt")),
        Map.entry("pos-tickets",spec(PosTicket.class,"property","FINANCE","createdAt","ticketNumber,outletCode,tableReference","id,ticketNumber,outletCode,tableReference,serviceDate,status,paymentMethod,currencyCode,netAmount,taxAmount,grossAmount,createdAt")),
        Map.entry("access-credentials",spec(AccessCredential.class,"property","FRONT_DESK","issuedAt","providerCode,externalReference","id,providerCode,externalReference,status,validFrom,validUntil,issuedBy,issuedAt,revokedAt")),
        Map.entry("migration-batches",spec(MigrationBatch.class,"property","INTEGRATIONS","createdAt","sourceSystem,idempotencyKey","id,idempotencyKey,sourceSystem,status,importedGuests,importedReservations,importedPayments,totalOpeningBalance,reconciliationMessage,createdAt,completedAt"))
    );
    public static String permissionFor(String section) { return requireSpec(section).permission(); }
    private static Spec requireSpec(String section) {
        Spec spec=SECTIONS.get(section); if(spec==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unbekannter Verlauf."); return spec;
    }
    @Transactional(readOnly=true)
    public Result page(String username,Long propertyId,String section,int page,int size,String query) {
        Spec spec=requireSpec(section); PmsPropertyAccessService access=accessProvider.getObject(); var actor=access.access(username); access.require(actor,propertyId,spec.permission(),false);
        if(page<0 || page>10000 || size<1 || size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Seite muss 0–10000 und Seitengröße 1–100 sein.");
        String search=query==null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if(search.length()>120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Suchtext ist zu lang.");
        String where=" where e."+spec.propertyPath()+".id = :propertyId and e."+spec.propertyPath()+".company.id = :companyId";
        if(!search.isEmpty()) where+=" and ("+Arrays.stream(spec.search().split(",")).map(field -> "lower(e."+field+") like :query escape '!' ").collect(java.util.stream.Collectors.joining(" or "))+")";
        String from=" from "+spec.entity().getSimpleName()+" e"+where;
        var count=entityManager.createQuery("select count(e)"+from,Long.class).setParameter("propertyId",propertyId).setParameter("companyId",actor.companyId());
        var rows=entityManager.createQuery("select "+spec.fields().stream().map(field -> "e."+field).collect(java.util.stream.Collectors.joining(","))+from+" order by e."+spec.order()+" desc,e.id desc",Object[].class)
                .setParameter("propertyId",propertyId).setParameter("companyId",actor.companyId()).setFirstResult(page*size).setMaxResults(size);
        if(!search.isEmpty()) { String escaped="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; count.setParameter("query",escaped);rows.setParameter("query",escaped); }
        long total=count.getSingleResult();
        List<Map<String,Object>> items=rows.getResultList().stream().map(row -> { Map<String,Object> item=new LinkedHashMap<>(); for(int index=0;index<spec.fields().size();index++) item.put(spec.fields().get(index),row[index]); return item; }).toList();
        return new Result(section,items,page,size,total,(long)(page+1)*size<total);
    }
    @Transactional(readOnly=true)
    public <T> List<T> newest(Class<T> entity,String propertyPath,Long propertyId,int limit) {
        Spec spec=SECTIONS.values().stream().filter(value -> value.entity()==entity && value.propertyPath().equals(propertyPath)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported PMS history model"));
        return entityManager.createQuery("select e from "+entity.getSimpleName()+" e where e."+propertyPath+".id=:propertyId order by e."+spec.order()+" desc,e.id desc",entity)
                .setParameter("propertyId",propertyId).setMaxResults(Math.max(1,Math.min(100,limit))).getResultList();
    }
}
