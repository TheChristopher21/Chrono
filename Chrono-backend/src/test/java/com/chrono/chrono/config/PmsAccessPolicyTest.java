package com.chrono.chrono.config;

import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsAccessPolicyTest {
    PmsPropertyAccessService service = mock(PmsPropertyAccessService.class);
    EntityManager em = mock(EntityManager.class);
    PmsAccessPolicy policy = new PmsAccessPolicy(service, em, new ObjectMapper());
    PmsPropertyAccessService.Access actor = new PmsPropertyAccessService.Access(7L, 10L, false,
            Map.of(5L, Map.of("FRONT_DESK", "MANAGE", "HOUSEKEEPING", "MANAGE", "GUESTS", "VIEW")));
    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(PmsAccessPolicy.ACCESS, actor); return request;
    }
    @Test void bodyScopedReservationAndNestedChannelBookingAreChecked() {
        var request = request("POST", "/api/pms/reservations");
        policy.authorize(request, Map.of("propertyId", 5L));
        verify(service).require(actor, 5L, "FRONT_DESK", true);
        var channel = request("POST", "/api/pms/integrations/bookings");
        policy.authorize(channel, Map.of("reservation", Map.of("propertyId", 9L)));
        verify(service).require(actor, 9L, "INTEGRATIONS", true);
    }
    @Test void unknownStaffWriteIsDeniedAndPrivacyRemainsMasterOnly() {
        assertThatThrownBy(() -> policy.authorize(request("POST", "/api/pms/new-sensitive-operation"), Map.of()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.authorize(request("GET", "/api/pms/privacy/guests/1/export"), null))
                .isInstanceOf(ResponseStatusException.class);
    }
    @SuppressWarnings("unchecked")
    @Test void staffDocumentsRequireExplicitHotelContextAndOrganizationRelationship() {
        assertThatThrownBy(() -> policy.authorize(request("GET", "/api/pms/organizations/1/documents"), null))
                .isInstanceOf(ResponseStatusException.class);
        var request = request("GET", "/api/pms/organizations/1/documents"); request.addParameter("propertyId", "5");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "1"));
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        assertThatThrownBy(() -> policy.authorize(request, null)).isInstanceOf(ResponseStatusException.class);
        when(query.getSingleResult()).thenReturn(1L);
        policy.authorize(request, null);
        verify(service).require(actor, 5L, "GUESTS", false);
    }
    @SuppressWarnings("unchecked")
    @Test void resourceIdLookupRequiresItsActualHotelAsWellAsBodyHotel() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(6L));
        var request = request("PUT", "/api/pms/reservations/88");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("reservationId", "88"));
        policy.authorize(request, Map.of("propertyId", 5L));
        verify(service).require(actor, 6L, "FRONT_DESK", true);
        verify(service).require(actor, 5L, "FRONT_DESK", true);
    }
    @Test void separatelyClassifiesRefundsRatesAndAccountingExports() {
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/payments/1/refund")).isEqualTo("REFUNDS");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/rate-plans/1/override")).isEqualTo("RATES");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/accounting-export.csv")).isEqualTo("REPORTS");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/payment-requests/1/capture")).isEqualTo("FINANCE");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/groups/1/routing")).isEqualTo("FINANCE");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/history/communications")).isEqualTo("GUESTS");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/receivables/1/refunds")).isEqualTo("REFUNDS");
    }
    @Test void newHotelModulesHaveExplicitReadWriteAndMasterBoundaries() {
        for (String endpoint : List.of("billing/bank-imports", "billing/export-runs", "payment-settings", "payment-automation", "international-settings", "invoices/9/ubl.xml"))
            assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/" + endpoint)).as(endpoint).isEqualTo("FINANCE");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/approvals/refunds/9/decision")).isEqualTo("REFUNDS");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/housekeeping/offline-commands")).isEqualTo("HOUSEKEEPING");
        assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/beds24/settings")).isEqualTo("INTEGRATIONS");
        for (String endpoint : List.of("billing/settings", "payment-settings", "international-settings", "approval-policy", "beds24/settings", "reports/revenue-planning/automation", "rate-inheritance")) {
            assertThat(PmsAccessPolicy.masterOnly("/api/pms/properties/5/" + endpoint, true)).as(endpoint).isTrue();
            assertThat(PmsAccessPolicy.masterOnly("/api/pms/properties/5/" + endpoint, false)).as(endpoint).isFalse();
        }
        assertThat(PmsAccessPolicy.masterOnly("/api/pms/properties/5/rate-inheritance/9/freeze", true)).isFalse();
        for (String endpoint : List.of("directory/organizations", "directory/organizations/5", "directory/groups", "directory/groups/5"))
            assertThat(PmsAccessPolicy.permissionFor("/api/pms/properties/5/" + endpoint)).as("Directory service checks OR permission: " + endpoint).isNull();
    }
}
