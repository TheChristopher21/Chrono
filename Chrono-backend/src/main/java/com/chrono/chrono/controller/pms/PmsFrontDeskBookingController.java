package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.CreateFrontDeskBookingRequest;
import com.chrono.chrono.dto.pms.FrontDeskBookingResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsFrontDeskBookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/pms")
public class PmsFrontDeskBookingController {
    private final PmsFrontDeskBookingService frontDeskBookingService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsFrontDeskBookingController(
            PmsFrontDeskBookingService frontDeskBookingService,
            UserRepository userRepository,
            UserPermissionService userPermissionService) {
        this.frontDeskBookingService = frontDeskBookingService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @PostMapping("/properties/{propertyId}/front-desk-bookings")
    public ResponseEntity<FrontDeskBookingResponse> createBooking(
            @PathVariable Long propertyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateFrontDeskBookingRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal);
        FrontDeskBookingResponse response = frontDeskBookingService.createBooking(
                context.company(),
                propertyId,
                idempotencyKey,
                request,
                context.username(),
                businessDate
        );
        return ResponseEntity.created(URI.create("/api/pms/reservations/" + response.reservationId()))
                .body(response);
    }

    private AccessContext requireContext(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(
                user,
                UserPermissionService.PAGE_PMS,
                UserPermissionService.ACCESS_MANAGE,
                "Die erforderliche PMS-Berechtigung fehlt."
        );
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return new AccessContext(user.getCompany(), user.getUsername());
    }

    private record AccessContext(Company company, String username) {
    }
}
