package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.ExternalBookingRequest;
import com.chrono.chrono.dto.pms.ChannelWebhookResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsPublicRateLimiter;
import com.chrono.chrono.services.pms.PmsWebhookSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmsChannelWebhookControllerTest {
    private final PmsWebhookSecurityService securityService = mock(PmsWebhookSecurityService.class);
    private final PmsAdvancedService advancedService = mock(PmsAdvancedService.class);
    private final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    private final Company company = new Company("Chrono Hotel AG");
    private final HotelProperty property = new HotelProperty();
    private final ChannelConnection connection = new ChannelConnection();
    private final PmsChannelWebhookController controller = new PmsChannelWebhookController(
            securityService,
            advancedService,
            new PmsPublicRateLimiter(30, 60),
            new ObjectMapper().findAndRegisterModules(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @BeforeEach
    void setUp() {
        company.setId(3L);
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        property.setCode("ZRH");
        connection.setProperty(property);
        connection.setProviderCode("CHANNEL_TEST");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(securityService.verify("0123456789abcdef0123456789abcdef", "100", "delivery-00000001", "signature", validBody()))
                .thenReturn(connection);
        when(advancedService.importExternalBookingForWebhook(
                eq(company), any(ExternalBookingRequest.class),
                eq("channel:CHANNEL_TEST")))
                .thenReturn(new ChannelWebhookResponse("accepted", "booking-4711", 9L, "CHR-1"));
    }

    @Test
    void importsVerifiedProviderScopedBooking() {
        var response = controller.receiveBooking(
                "0123456789abcdef0123456789abcdef", "100", "delivery-00000001", "signature", validBody(), servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(advancedService).importExternalBookingForWebhook(
                eq(company), any(ExternalBookingRequest.class),
                eq("channel:CHANNEL_TEST"));
        assertThat(response.getBody()).extracting(ChannelWebhookResponse::externalId)
                .isEqualTo("booking-4711");
    }

    @Test
    void verifiesSignatureBeforeRejectingMalformedJson() {
        when(securityService.verify(
                "0123456789abcdef0123456789abcdef", "100", "delivery-00000002", "signature", "{not-json"))
                .thenReturn(connection);

        assertThatThrownBy(() -> controller.receiveBooking(
                "0123456789abcdef0123456789abcdef", "100", "delivery-00000002", "signature", "{not-json", servletRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(securityService).verify(
                "0123456789abcdef0123456789abcdef", "100", "delivery-00000002", "signature", "{not-json");
        verify(advancedService, never()).importExternalBookingForWebhook(
                any(), any(), any());
    }

    private String validBody() {
        return """
                {
                  "channel": "CHANNEL_TEST",
                  "externalId": "booking-4711",
                  "reservation": {
                    "propertyId": 5,
                    "guestId": 7,
                    "roomTypeId": 10,
                    "roomId": null,
                    "ratePlanId": 20,
                    "arrivalDate": "%s",
                    "departureDate": "%s",
                    "adults": 2,
                    "children": 0,
                    "status": "CONFIRMED",
                    "source": "CHANNEL_MANAGER"
                  }
                }
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
    }
}
