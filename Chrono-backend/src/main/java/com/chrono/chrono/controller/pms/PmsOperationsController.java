package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/pms")
public class PmsOperationsController {

    private final PmsOperationsService operationsService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsOperationsController(PmsOperationsService operationsService,
                                   UserRepository userRepository,
                                   UserPermissionService userPermissionService) {
        this.operationsService = operationsService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping("/operations")
    public ResponseEntity<PmsOperationsResponse> getOperations(
            @RequestParam Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(operationsService.getOperations(
                context.company(),
                propertyId,
                businessDate,
                from,
                to
        ));
    }

    @GetMapping("/properties/{propertyId}/availability")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable Long propertyId,
            @RequestParam LocalDate arrival,
            @RequestParam LocalDate departure,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(operationsService.getAvailability(
                context.company(),
                propertyId,
                arrival,
                departure
        ));
    }

    @PostMapping("/properties/{propertyId}/guests")
    public ResponseEntity<PmsOperationsResponse> createGuest(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertGuestRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/guests"))
                .body(operationsService.createGuest(context.company(), propertyId, request, businessDate));
    }

    @PutMapping("/properties/{propertyId}/guests/{guestId}")
    public ResponseEntity<PmsOperationsResponse> updateGuest(
            @PathVariable Long propertyId,
            @PathVariable Long guestId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertGuestRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.updateGuest(
                context.company(),
                propertyId,
                guestId,
                request,
                businessDate
        ));
    }

    @PostMapping("/properties/{propertyId}/rate-plans")
    public ResponseEntity<PmsOperationsResponse> createRatePlan(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertRatePlanRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/rate-plans"))
                .body(operationsService.createRatePlan(context.company(), propertyId, request, businessDate));
    }

    @PutMapping("/properties/{propertyId}/rate-plans/{ratePlanId}")
    public ResponseEntity<PmsOperationsResponse> updateRatePlan(
            @PathVariable Long propertyId,
            @PathVariable Long ratePlanId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertRatePlanRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.updateRatePlan(
                context.company(),
                propertyId,
                ratePlanId,
                request,
                businessDate
        ));
    }

    @PutMapping("/properties/{propertyId}/rate-plans/{ratePlanId}/override")
    public ResponseEntity<PmsOperationsResponse> upsertRateOverride(
            @PathVariable Long propertyId,
            @PathVariable Long ratePlanId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertRateOverrideRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.upsertRateOverride(
                context.company(),
                propertyId,
                ratePlanId,
                request,
                businessDate
        ));
    }

    @PostMapping("/reservations")
    public ResponseEntity<PmsOperationsResponse> createReservation(
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertReservationRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/reservations"))
                .body(operationsService.createReservation(
                        context.company(),
                        request,
                        context.username(),
                        businessDate
                ));
    }

    @PutMapping("/reservations/{reservationId}")
    public ResponseEntity<PmsOperationsResponse> updateReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertReservationRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.updateReservation(
                context.company(),
                reservationId,
                request,
                context.username(),
                businessDate
        ));
    }

    @PostMapping("/reservations/{reservationId}/check-in")
    public ResponseEntity<PmsOperationsResponse> checkIn(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.checkIn(
                context.company(), reservationId, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/check-out")
    public ResponseEntity<PmsOperationsResponse> checkOut(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.checkOut(
                context.company(), reservationId, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<PmsOperationsResponse> cancelReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody(required = false) ReservationLifecycleRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.cancelReservation(
                context.company(),
                reservationId,
                request,
                context.username(),
                businessDate
        ));
    }

    @PostMapping("/reservations/{reservationId}/no-show")
    public ResponseEntity<PmsOperationsResponse> markNoShow(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.markNoShow(
                context.company(), reservationId, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<PmsOperationsResponse> confirmReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody(required = false) ReservationLifecycleRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.confirmReservation(
                context.company(), reservationId, request, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/offer")
    public ResponseEntity<PmsOperationsResponse> offerReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody(required = false) ReservationLifecycleRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.changeLifecycleStatus(
                context.company(), reservationId, com.chrono.chrono.entities.pms.ReservationStatus.OFFERED,
                request, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/tentative")
    public ResponseEntity<PmsOperationsResponse> holdReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody(required = false) ReservationLifecycleRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.changeLifecycleStatus(
                context.company(), reservationId, com.chrono.chrono.entities.pms.ReservationStatus.TENTATIVE,
                request, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/waitlist")
    public ResponseEntity<PmsOperationsResponse> waitlistReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody(required = false) ReservationLifecycleRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.changeLifecycleStatus(
                context.company(), reservationId, com.chrono.chrono.entities.pms.ReservationStatus.WAITLISTED,
                request, context.username(), businessDate));
    }

    @PostMapping("/reservations/{reservationId}/move-room")
    public ResponseEntity<PmsOperationsResponse> moveReservationRoom(
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody MoveReservationRoomRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.moveReservationRoom(
                context.company(), reservationId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/folios/{folioId}/items")
    public ResponseEntity<PmsOperationsResponse> postFolioItem(
            @PathVariable Long propertyId,
            @PathVariable Long folioId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody PostFolioItemRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.postFolioItem(
                context.company(),
                propertyId,
                folioId,
                request,
                businessDate
        ));
    }

    @PostMapping("/properties/{propertyId}/folios/{folioId}/payments")
    public ResponseEntity<PmsOperationsResponse> postPayment(
            @PathVariable Long propertyId,
            @PathVariable Long folioId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody PostPaymentRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.postPayment(
                context.company(),
                propertyId,
                folioId,
                request,
                context.username(),
                businessDate
        ));
    }

    @PostMapping("/properties/{propertyId}/payments/{paymentId}/refund")
    public ResponseEntity<PmsOperationsResponse> refundPayment(
            @PathVariable Long propertyId,
            @PathVariable Long paymentId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody RefundPaymentRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.refundPayment(
                context.company(), propertyId, paymentId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/payments/{paymentId}/void")
    public ResponseEntity<PmsOperationsResponse> voidPayment(
            @PathVariable Long propertyId,
            @PathVariable Long paymentId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody VoidPaymentRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.voidPayment(
                context.company(), propertyId, paymentId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/cash-shifts/open")
    public ResponseEntity<PmsOperationsResponse> openCashShift(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody OpenCashShiftRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.openCashShift(
                context.company(), propertyId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/cash-shifts/close")
    public ResponseEntity<PmsOperationsResponse> closeCashShift(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CloseCashShiftRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.closeCashShift(
                context.company(), propertyId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/maintenance")
    public ResponseEntity<PmsOperationsResponse> createMaintenanceWorkOrder(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateMaintenanceWorkOrderRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/maintenance"))
                .body(operationsService.createMaintenanceWorkOrder(
                        context.company(), propertyId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/maintenance/{workOrderId}/resolve")
    public ResponseEntity<PmsOperationsResponse> resolveMaintenanceWorkOrder(
            @PathVariable Long propertyId,
            @PathVariable Long workOrderId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody ResolveMaintenanceWorkOrderRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.resolveMaintenanceWorkOrder(
                context.company(), propertyId, workOrderId, request, context.username(), businessDate));
    }

    @PutMapping("/properties/{propertyId}/housekeeping/{taskId}")
    public ResponseEntity<PmsOperationsResponse> updateHousekeepingTask(
            @PathVariable Long propertyId,
            @PathVariable Long taskId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpdateHousekeepingTaskRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(operationsService.updateHousekeepingTask(
                context.company(),
                propertyId,
                taskId,
                request,
                businessDate
        ));
    }

    private AccessContext requireContext(Principal principal, String accessLevel) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(
                user,
                UserPermissionService.PAGE_PMS,
                accessLevel,
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
