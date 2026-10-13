package com.chrono.chrono.config;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@ControllerAdvice(basePackages = "com.chrono.chrono.controller.pms")
public class PmsAccessResponseAdvice implements ResponseBodyAdvice<Object> {
    private final PmsAccessPolicy policy;
    private final ObjectMapper mapper;
    public PmsAccessResponseAdvice(PmsAccessPolicy policy, ObjectMapper mapper) { this.policy = policy; this.mapper = mapper; }
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet) || !policy.applies(servlet.getServletRequest()) || body == null) return body;
        var actor = policy.access(servlet.getServletRequest());
        if (actor.master()) return body;
        if (body instanceof PmsSetupResponse setup) return filterSetup(setup, actor);
        if (body instanceof PmsPortfolioResponse portfolio) return filterPortfolio(portfolio, actor);
        Long propertyId = (Long) servlet.getServletRequest().getAttribute(PmsAccessPolicy.PROPERTY);
        if (propertyId == null) return body;
        if (!(body instanceof PmsOperationsResponse || body instanceof PmsAdvancedResponse
                || body instanceof PmsExtensionsResponse || body instanceof PmsRoomPlanResponse || body instanceof FrontDeskBookingResponse)) return body;
        ObjectNode json = mapper.valueToTree(body);
        filterPayload(json, actor, propertyId, body.getClass());
        return json;
    }

    Object filterSetup(PmsSetupResponse setup, PmsPropertyAccessService.Access actor) {
        var visible = setup.properties().stream().filter(property -> actor.any(property.id())).toList();
        int types = visible.stream().mapToInt(property -> property.roomTypes().size()).sum();
        int rooms = (int) visible.stream().flatMap(property -> property.roomTypes().stream()).mapToLong(PmsSetupResponse.RoomTypeView::roomCount).sum();
        return new PmsSetupResponse(visible, visible.size(), types, rooms, !visible.isEmpty() && types > 0 && rooms > 0);
    }

    Object filterPortfolio(PmsPortfolioResponse portfolio, PmsPropertyAccessService.Access actor) {
        var hotels = portfolio.hotels().stream().filter(property -> actor.allows(property.propertyId(), "REPORTS", false)).toList();
        long available = hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::availableRooms).sum();
        long sold = hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::soldRooms).sum();
        return new PmsPortfolioResponse(portfolio.businessDate(), hotels.size(),
                hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::operationalRooms).sum(), available, sold,
                available == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(sold).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP),
                hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::arrivals).sum(),
                hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::departures).sum(), hotels);
    }

    void filterPayload(ObjectNode json, PmsPropertyAccessService.Access actor, Long propertyId, Class<?> type) {
        boolean desk = actor.allows(propertyId, "FRONT_DESK", false);
        boolean guests = actor.allows(propertyId, "GUESTS", false);
        boolean finance = actor.allows(propertyId, "FINANCE", false);
        boolean housekeeping = actor.allows(propertyId, "HOUSEKEEPING", false);
        boolean integrations = actor.allows(propertyId, "INTEGRATIONS", false);
        boolean rates = desk || actor.allows(propertyId, "RATES", false);
        if (type == FrontDeskBookingResponse.class) {
            if (json.get("operations") instanceof ObjectNode operations) filterPayload(operations,actor,propertyId,PmsOperationsResponse.class);
            if (!finance) json.putNull("balance");
        } else if (type == PmsOperationsResponse.class) {
            restrict(json, desk, "reservations", "arrivals", "departures");
            restrict(json, guests, "guests", "organizations");
            restrict(json, finance, "folios");
            if (!finance) { json.putNull("cashShift"); if (json.get("metrics") instanceof ObjectNode metrics) { metrics.putNull("openBalance"); metrics.put("openFolios", 0); } }
            restrict(json, rates, "ratePlans", "rateOverrides");
            restrict(json, housekeeping, "housekeepingTasks", "maintenanceWorkOrders");
            if (!desk) for (JsonNode room : json.path("rooms")) {
                if (room instanceof ObjectNode object && object.get("currentReservation") instanceof ObjectNode reservation) scrubReservation(reservation);
            }
        } else if (type == PmsAdvancedResponse.class) {
            restrict(json, guests, "organizations", "communications", "guestRegistrations", "communicationTemplates");
            restrict(json, finance, "invoices", "nightAudits");
            restrict(json, desk, "groups", "hotelResources", "resourceBookings");
            restrict(json, integrations, "integrationOutbox", "channelConnections", "auditEvents");
        } else if (type == PmsExtensionsResponse.class) {
            restrict(json, finance, "posTickets");
            restrict(json, desk, "accessCredentials");
            restrict(json, integrations, "migrationBatches");
            if (!integrations) { json.putNull("bookingEngine"); json.putNull("tourismTax"); }
        } else if (type == PmsRoomPlanResponse.class && !desk) {
            for (JsonNode item : json.path("reservations")) if (item instanceof ObjectNode reservation) scrubReservation(reservation);
        }
    }
    private void restrict(ObjectNode object, boolean allowed, String... names) {
        if (!allowed) for (String name : names) object.set(name, mapper.createArrayNode());
    }
    private void scrubReservation(ObjectNode reservation) {
        Set<String> allowed = Set.of("id", "version", "roomId", "roomTypeId", "roomNumber", "roomTypeName", "arrivalDate", "departureDate", "status", "adults", "children", "segmentId", "segmentStartDate", "segmentEndDate");
        List<String> remove = new ArrayList<>(); reservation.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) remove.add(key); });
        reservation.remove(remove); reservation.put("guestName", "Belegt");
    }
}
