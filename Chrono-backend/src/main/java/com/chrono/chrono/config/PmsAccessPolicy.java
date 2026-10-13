package com.chrono.chrono.config;

import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import java.util.*;

/** Applies hotel grants at the shared HTTP boundary, including body-scoped and
 * resource-id routes. Existing tenant/page checks remain an additional boundary. */
@Component
public class PmsAccessPolicy {
    public static final String ACCESS = PmsAccessPolicy.class.getName() + ".access";
    public static final String PROPERTY = PmsAccessPolicy.class.getName() + ".property";
    public static final String LIVE_AUTHORIZED = PmsAccessPolicy.class.getName() + ".liveAuthorized";
    private final PmsPropertyAccessService service;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private static final Map<String, String> ENTITIES = Map.of(
            "reservationId", "Reservation", "invoiceId", "PmsInvoice", "roomId", "Room", "roomTypeId", "RoomType");

    public PmsAccessPolicy(PmsPropertyAccessService service, EntityManager entityManager, ObjectMapper mapper) {
        this.service = service; this.entityManager = entityManager; this.mapper = mapper;
    }
    public boolean applies(HttpServletRequest request) {
        String path = path(request);
        return path.startsWith("/api/pms/") && !path.startsWith("/api/pms/session/") && !path.startsWith("/api/pms/access/");
    }
    public PmsPropertyAccessService.Access access(HttpServletRequest request) {
        Object stored = request.getAttribute(ACCESS);
        if (stored instanceof PmsPropertyAccessService.Access result) return result;
        var principal = request.getUserPrincipal();
        var result = service.access(principal == null ? null : principal.getName());
        request.setAttribute(ACCESS, result);
        return result;
    }
    public void authorize(HttpServletRequest request, Object body) {
        if (!applies(request)) return;
        var actor = access(request);
        String path = path(request);
        boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        String permission = write && (path.endsWith("/reports/revenue-planning/automation") || path.endsWith("/reports/revenue-planning/pricing/apply")) ? "RATES" : permissionFor(path);
        if (masterOnly(path, write) && !actor.master()) throw denied();
        Set<Long> propertyIds = resolvePropertyIds(request, actor.companyId());
        if (path.contains("/documents") && !actor.master()) {
            Long propertyId = request.getParameter("propertyId") == null ? null : positive(request.getParameter("propertyId"));
            if (propertyId == null) throw denied();
            requireDocumentScope(request, actor.companyId(), propertyId);
        }
        if (body != null) collectPropertyIds(mapper.valueToTree(body), propertyIds);
        if (propertyIds.isEmpty()) {
            if (path.equals("/api/pms/setup") || path.equals("/api/pms/reports/portfolio")) return;
            if (actor.master()) return;
            // Body-scoped writes are checked again after request conversion.
            if (body == null && write && (path.equals("/api/pms/reservations")
                    || path.equals("/api/pms/groups") || path.equals("/api/pms/integrations/bookings"))) return;
            if (!actor.any() || write) throw denied();
            if (permission != null && actor.grants().keySet().stream().noneMatch(id -> actor.allows(id, permission, false))) throw denied();
            return;
        }
        for (Long propertyId : propertyIds) service.require(actor, propertyId, permission, write);
        if (body instanceof com.chrono.chrono.dto.pms.CreateFrontDeskBookingRequest booking
                && (booking.newGuest()!=null || booking.registration()!=null)) {
            for(Long propertyId:propertyIds) service.require(actor,propertyId,"GUESTS",true);
        }
        request.setAttribute(PROPERTY, propertyIds.iterator().next());
    }
    @SuppressWarnings("unchecked")
    private void requireDocumentScope(HttpServletRequest request, Long companyId, Long propertyId) {
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Long organizationId = null;
        String rateId = request.getParameter("ratePlanId");
        if (rateId != null && !rateId.isBlank()) {
            List<Long> rates = entityManager.createQuery("select r.property.id from RatePlan r where r.id = :id and r.property.company.id = :companyId", Long.class)
                    .setParameter("id", positive(rateId)).setParameter("companyId", companyId).getResultList();
            if (rates.isEmpty() || !propertyId.equals(rates.get(0))) throw denied();
        }
        if (path(request).startsWith("/api/pms/documents/")) {
            List<Object[]> documents = entityManager.createQuery("select d.organization.id, r.property.id from PmsProfileDocument d left join d.ratePlan r where d.id = :id and d.company.id = :companyId", Object[].class)
                    .setParameter("id", positive(variables.get("id"))).setParameter("companyId", companyId).getResultList();
            if (documents.isEmpty() || documents.get(0)[1] != null && !propertyId.equals(documents.get(0)[1])) throw denied();
            organizationId = (Long) documents.get(0)[0];
        } else if (variables != null && variables.containsKey("id")) {
            organizationId = positive(variables.get("id"));
        }
        if (organizationId == null) throw denied();
        Long relatedRates = entityManager.createQuery("select count(r) from RatePlan r where r.organization.id = :organizationId and r.property.id = :propertyId and r.property.company.id = :companyId", Long.class)
                .setParameter("organizationId", organizationId).setParameter("propertyId", propertyId).setParameter("companyId", companyId).getSingleResult();
        if (relatedRates > 0) return;
        Long relatedReservations = entityManager.createQuery("select count(r) from Reservation r where r.guest.organization.id = :organizationId and r.property.id = :propertyId and r.property.company.id = :companyId", Long.class)
                .setParameter("organizationId", organizationId).setParameter("propertyId", propertyId).setParameter("companyId", companyId).getSingleResult();
        if (relatedReservations == 0) throw denied();
    }

    @SuppressWarnings("unchecked")
    private Set<Long> resolvePropertyIds(HttpServletRequest request, Long companyId) {
        Set<Long> ids = new LinkedHashSet<>();
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) variables = Map.of();
        if (variables.containsKey("propertyId")) ids.add(positive(variables.get("propertyId")));
        if (request.getParameter("propertyId") != null) ids.add(positive(request.getParameter("propertyId")));
        for (var entry : ENTITIES.entrySet()) {
            if (!variables.containsKey(entry.getKey())) continue;
            Long id = positive(variables.get(entry.getKey()));
            List<Long> found = entityManager.createQuery("select e.property.id from " + entry.getValue()
                    + " e where e.id = :id and e.property.company.id = :companyId", Long.class)
                    .setParameter("id", id).setParameter("companyId", companyId).getResultList();
            if (found.isEmpty()) throw denied();
            ids.add(found.get(0));
        }
        return ids;
    }
    private void collectPropertyIds(JsonNode node, Set<Long> ids) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            if ("propertyId".equals(field.getKey()) && !field.getValue().isNull()) {
                JsonNode value = field.getValue();
                if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiges Hotel.");
                ids.add(value.longValue());
            } else if (field.getValue().isContainerNode()) collectPropertyIds(field.getValue(), ids);
        });
        else if (node.isArray()) node.forEach(item -> collectPropertyIds(item, ids));
    }
    private Long positive(String value) {
        try { long result = Long.parseLong(value); if (result > 0) return result; }
        catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiges Hotel oder Datensatz.");
    }
    public static String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
    static boolean masterOnly(String path, boolean write) {
        return path.startsWith("/api/pms/privacy/")
                || write && (path.equals("/api/pms/properties") || path.matches("/api/pms/properties/[^/]+")
                || path.contains("/room-types") || path.matches("/api/pms/(?:properties/[^/]+/)?rooms(?:/.*)?")
                || path.contains("/booking-engine") || path.endsWith("/tourism-tax")
                || path.contains("/migration-batches") || path.contains("/communication-templates")
                || path.contains("/payment-settings") || path.endsWith("/billing/settings")
                || path.endsWith("/approval-policy") || path.endsWith("/international-settings")
                || path.endsWith("/beds24/settings")
                || path.endsWith("/reports/revenue-planning/automation")
                || path.contains("/rate-inheritance") && !path.endsWith("/freeze")
                || path.contains("/channel-connections") && !path.endsWith("/sync")
                || path.endsWith("/resources"));
    }
    static String permissionFor(String path) {
        // The directory service enforces an explicit OR across the modules that use each picker.
        if (path.matches("/api/pms/properties/[^/]+/directory/(organizations|groups)(/[^/]+)?")) return null;
        if(path.matches("/api/pms/properties/[^/]+/history/[^/]+")) return com.chrono.chrono.services.pms.PmsHistoryService.permissionFor(path.substring(path.lastIndexOf('/')+1));
        if(path.matches("/api/pms/properties/[^/]+/groups/[^/]+/routing")) return "FINANCE";
        if(path.endsWith("/event-order/post")) return "FINANCE";
        if(path.endsWith("/accounting-settings")) return "REPORTS";
        if (path.contains("/billing/") || path.contains("/payment-settings") || path.contains("/payment-automation") || path.endsWith("/international-settings")) return "FINANCE";
        if (path.contains("/approvals/") || path.endsWith("/approval-policy")) return "REFUNDS";
        if (path.contains("/rate-inheritance")) return "RATES";
        if (path.endsWith("/refund") || path.endsWith("/refunds") || path.endsWith("/void") || path.endsWith("/correct")) return "REFUNDS";
        if (path.contains("/reports/") || path.endsWith("accounting-export.csv")) return "REPORTS";
        if (path.contains("/housekeeping") || path.contains("/maintenance")) return "HOUSEKEEPING";
        if (path.contains("/rate-plans")) return "RATES";
        if (path.contains("/folios") || path.contains("/payments") || path.contains("/cash-shifts")
                || path.contains("/invoices") || path.contains("/night-audits") || path.contains("/pos/")
                || path.endsWith("/tourism-tax/postings") || path.contains("/receivables")
                || path.contains("/credit-account") || path.contains("/financial-day") || path.contains("/payment-requests")) return "FINANCE";
        if (path.contains("/integrations") || path.contains("/integration-outbox") || path.contains("/channel-connections") || path.contains("/beds24/")) return "INTEGRATIONS";
        if (path.contains("/guests") || path.contains("/organizations") || path.contains("/documents") || path.contains("/communications")
                || path.contains("/inbox") || path.contains("/guest-registration") || path.endsWith("/document-limits")) return "GUESTS";
        if (path.contains("/reservations") || path.contains("/front-desk-bookings") || path.contains("/groups")
                || path.endsWith("/availability") || path.contains("/resource-bookings") || path.contains("/access-credentials")) return "FRONT_DESK";
        // Broad read DTOs are reduced in PmsAccessResponseAdvice. Unknown writes
        // are denied to staff rather than implicitly inheriting a broad grant.
        return null;
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Keine Berechtigung für diesen Hotelbereich."); }
}
