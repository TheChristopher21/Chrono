package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.CreateFrontDeskBookingRequest;
import com.chrono.chrono.dto.pms.CompleteGuestRegistrationRequest;
import com.chrono.chrono.dto.pms.FrontDeskBookingResponse;
import com.chrono.chrono.dto.pms.UpsertGuestRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.GuestRegistrationStatus;
import com.chrono.chrono.entities.pms.ReservationSource;
import com.chrono.chrono.entities.pms.ReservationStatus;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsFrontDeskBookingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsFrontDeskBookingControllerTest {
    @Mock private PmsFrontDeskBookingService service;
    @Mock private UserRepository userRepository;
    @Mock private UserPermissionService permissionService;

    @Test
    void createsBookingForPmsManagerAndForwardsIdempotencyKey() {
        Company company = new Company("Hotel Test AG");
        company.setId(41L);
        User user = new User();
        user.setUsername("reception.one");
        user.setCompany(company);
        Principal principal = user::getUsername;
        CreateFrontDeskBookingRequest request = request();
        FrontDeskBookingResponse result = new FrontDeskBookingResponse(
                7L, 8L, 9L, "CHR-12345678", new BigDecimal("145.00"), new BigDecimal("145.00"),
                GuestRegistrationStatus.PENDING, ReservationStatus.CONFIRMED, false,
                List.of("Vor dem Check-in müssen die Meldedaten vollständig erfasst werden."), null);
        when(userRepository.findByUsernameWithPermissionContext("reception.one")).thenReturn(Optional.of(user));
        when(service.createBooking(company, 3L, "front-desk-controller-01", request,
                "reception.one", LocalDate.of(2026, 9, 1))).thenReturn(result);
        PmsFrontDeskBookingController controller =
                new PmsFrontDeskBookingController(service, userRepository, permissionService);

        ResponseEntity<FrontDeskBookingResponse> response = controller.createBooking(
                3L,
                "front-desk-controller-01",
                LocalDate.of(2026, 9, 1),
                request,
                principal
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/pms/reservations/8");
        assertThat(response.getBody()).isSameAs(result);
        verify(permissionService).assertPageAccess(
                user,
                UserPermissionService.PAGE_PMS,
                UserPermissionService.ACCESS_MANAGE,
                "Die erforderliche PMS-Berechtigung fehlt."
        );
        verify(service).createBooking(company, 3L, "front-desk-controller-01", request,
                "reception.one", LocalDate.of(2026, 9, 1));
    }

    @Test
    void rejectsAnonymousRequest() {
        PmsFrontDeskBookingController controller =
                new PmsFrontDeskBookingController(service, userRepository, permissionService);

        assertThatThrownBy(() -> controller.createBooking(
                3L, "front-desk-controller-01", null, request(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Authentifizierung");
    }

    @Test
    void requestValidationEnforcesFrontDeskHardeningRules() {
        CreateFrontDeskBookingRequest base = request();
        CreateFrontDeskBookingRequest noContact = new CreateFrontDeskBookingRequest(
                null,
                new UpsertGuestRequest("Nina", "Meier", " ", null, null, null, null, null, false),
                base.roomTypeId(), base.roomId(), base.ratePlanId(), base.arrivalDate(), base.departureDate(),
                base.adults(), base.children(), ReservationSource.DIRECT, null, null, null, false);
        CreateFrontDeskBookingRequest walkInWithoutCheckIn = new CreateFrontDeskBookingRequest(
                base.existingGuestId(), null, base.roomTypeId(), base.roomId(), base.ratePlanId(),
                base.arrivalDate(), base.departureDate(), base.adults(), base.children(),
                ReservationSource.WALK_IN, null, null, null, false);
        CreateFrontDeskBookingRequest checkInWithoutRoom = new CreateFrontDeskBookingRequest(
                base.existingGuestId(), null, base.roomTypeId(), null, base.ratePlanId(),
                base.arrivalDate(), base.departureDate(), base.adults(), base.children(),
                ReservationSource.WALK_IN, null, null, registration(), true);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(noContact))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("Für einen neuen Gast muss eine E-Mail-Adresse oder Telefonnummer angegeben werden.");
            assertThat(factory.getValidator().validate(walkInWithoutCheckIn))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("WALK_IN und sofortiger Check-in müssen gemeinsam gewählt werden.");
            assertThat(factory.getValidator().validate(checkInWithoutRoom))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("Für einen direkten Check-in muss ein Zimmer zugewiesen sein.");
        }
    }

    private CreateFrontDeskBookingRequest request() {
        return new CreateFrontDeskBookingRequest(
                7L,
                null,
                2L,
                4L,
                5L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                1,
                0,
                ReservationSource.DIRECT,
                null,
                null,
                null,
                false
        );
    }

    private CompleteGuestRegistrationRequest registration() {
        return new CompleteGuestRegistrationRequest(
                "Seestrasse 10", "8002", "Zürich", "CH", "CH", "X123456789",
                null, "Nina Meier", true, null, null);
    }
}
