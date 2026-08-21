package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsReportingService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/pms")
public class PmsAdvancedController {

    private final PmsAdvancedService advancedService;
    private final PmsReportingService reportingService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsAdvancedController(PmsAdvancedService advancedService,
                                 PmsReportingService reportingService,
                                 UserRepository userRepository,
                                 UserPermissionService userPermissionService) {
        this.advancedService = advancedService;
        this.reportingService = reportingService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping("/advanced")
    public ResponseEntity<PmsAdvancedResponse> getAdvanced(@RequestParam Long propertyId,
                                                           @RequestParam(required = false) LocalDate businessDate,
                                                           Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(advancedService.getAdvanced(context.company(), propertyId, businessDate));
    }

    @GetMapping("/reports/performance")
    public ResponseEntity<PmsPerformanceReportResponse> getPerformanceReport(
            @RequestParam Long propertyId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDateExclusive,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(reportingService.performance(
                context.company(), propertyId, fromDate, toDateExclusive));
    }

    @GetMapping("/reports/portfolio")
    public ResponseEntity<PmsPortfolioResponse> getPortfolio(
            @RequestParam LocalDate businessDate,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(reportingService.portfolio(context.company(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/resources")
    public ResponseEntity<PmsAdvancedResponse> createHotelResource(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertHotelResourceRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/resources"))
                .body(advancedService.createHotelResource(
                        context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/resource-bookings")
    public ResponseEntity<PmsAdvancedResponse> createResourceBooking(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateResourceBookingRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/resource-bookings"))
                .body(advancedService.createResourceBooking(
                        context.company(), propertyId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/resource-bookings/{bookingId}/cancel")
    public ResponseEntity<PmsAdvancedResponse> cancelResourceBooking(
            @PathVariable Long propertyId,
            @PathVariable Long bookingId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.cancelResourceBooking(
                context.company(), propertyId, bookingId, businessDate));
    }

    @PostMapping("/properties/{propertyId}/organizations")
    public ResponseEntity<PmsAdvancedResponse> createOrganization(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertOrganizationRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/organizations"))
                .body(advancedService.createOrganization(context.company(), propertyId, request, businessDate));
    }

    @PutMapping("/properties/{propertyId}/organizations/{organizationId}")
    public ResponseEntity<PmsAdvancedResponse> updateOrganization(
            @PathVariable Long propertyId,
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertOrganizationRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.updateOrganization(
                context.company(), propertyId, organizationId, request, businessDate));
    }

    @PostMapping("/groups")
    public ResponseEntity<PmsAdvancedResponse> createGroup(
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateGroupBookingRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/groups"))
                .body(advancedService.createGroupBooking(
                        context.company(), request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/folios")
    public ResponseEntity<PmsOperationsResponse> createSplitFolio(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateSplitFolioRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/folios"))
                .body(advancedService.createSplitFolio(context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/folios/{sourceFolioId}/move-items")
    public ResponseEntity<PmsOperationsResponse> moveFolioItems(
            @PathVariable Long propertyId,
            @PathVariable Long sourceFolioId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody MoveFolioItemsRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.moveFolioItems(
                context.company(), propertyId, sourceFolioId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/invoices")
    public ResponseEntity<PmsAdvancedResponse> createInvoice(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateInvoiceRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/invoices"))
                .body(advancedService.createInvoice(context.company(), propertyId, request, businessDate));
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable Long invoiceId, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        byte[] pdf = advancedService.generateInvoicePdf(context.company(), invoiceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("pms-rechnung-" + invoiceId + ".pdf", StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping("/properties/{propertyId}/invoices/{invoiceId}/correct")
    public ResponseEntity<PmsAdvancedResponse> correctInvoice(
            @PathVariable Long propertyId,
            @PathVariable Long invoiceId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CorrectInvoiceRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.correctInvoice(
                context.company(), propertyId, invoiceId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/night-audits")
    public ResponseEntity<PmsAdvancedResponse> closeNightAudit(
            @PathVariable Long propertyId,
            @Valid @RequestBody CloseNightAuditRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/night-audits"))
                .body(advancedService.closeNightAudit(context.company(), propertyId, request, context.username()));
    }

    @PostMapping("/properties/{propertyId}/housekeeping")
    public ResponseEntity<PmsOperationsResponse> createHousekeepingTask(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateHousekeepingTaskRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/housekeeping"))
                .body(advancedService.createHousekeepingTask(context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/communication-templates")
    public ResponseEntity<PmsAdvancedResponse> createTemplate(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertCommunicationTemplateRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/communication-templates"))
                .body(advancedService.createTemplate(context.company(), propertyId, request, businessDate));
    }

    @PutMapping("/properties/{propertyId}/communication-templates/{templateId}")
    public ResponseEntity<PmsAdvancedResponse> updateTemplate(
            @PathVariable Long propertyId,
            @PathVariable Long templateId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody UpsertCommunicationTemplateRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.updateTemplate(
                context.company(), propertyId, templateId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/communications")
    public ResponseEntity<PmsAdvancedResponse> queueCommunication(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody QueueCommunicationRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.accepted().body(advancedService.queueCommunication(
                context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/inbox/messages")
    public ResponseEntity<PmsAdvancedResponse> recordInboundCommunication(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody PostInboundCommunicationRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/inbox/messages"))
                .body(advancedService.recordInboundCommunication(
                        context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/inbox/replies")
    public ResponseEntity<PmsAdvancedResponse> queueInboxReply(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody QueueInboxReplyRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.accepted().body(advancedService.queueInboxReply(
                context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/inbox/messages/{communicationId}/read")
    public ResponseEntity<PmsAdvancedResponse> markCommunicationRead(
            @PathVariable Long propertyId,
            @PathVariable Long communicationId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.markCommunicationRead(
                context.company(), propertyId, communicationId, businessDate));
    }

    @PostMapping("/properties/{propertyId}/integration-outbox/{eventId}/acknowledge")
    public ResponseEntity<PmsAdvancedResponse> acknowledgeOutbox(
            @PathVariable Long propertyId,
            @PathVariable Long eventId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.acknowledgeOutboxEvent(
                context.company(), propertyId, eventId, businessDate));
    }

    @PostMapping("/properties/{propertyId}/integration-outbox/{eventId}/retry")
    public ResponseEntity<PmsAdvancedResponse> retryOutbox(
            @PathVariable Long propertyId,
            @PathVariable Long eventId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.retryOutboxEvent(
                context.company(), propertyId, eventId, businessDate));
    }

    @PostMapping("/integrations/bookings")
    public ResponseEntity<PmsOperationsResponse> importExternalBooking(
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody ExternalBookingRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.importExternalBooking(
                context.company(), request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/channel-connections")
    public ResponseEntity<PmsAdvancedResponse> createChannelConnection(
            @PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CreateChannelConnectionRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/channel-connections"))
                .body(advancedService.createChannelConnection(
                        context.company(), propertyId, request, businessDate));
    }

    @PostMapping("/properties/{propertyId}/channel-connections/{connectionId}/sync")
    public ResponseEntity<PmsAdvancedResponse> syncChannelConnection(
            @PathVariable Long propertyId,
            @PathVariable Long connectionId,
            @RequestParam(required = false) LocalDate businessDate,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.syncChannelConnection(
                context.company(), propertyId, connectionId, businessDate));
    }

    @PostMapping("/properties/{propertyId}/reservations/{reservationId}/guest-registration")
    public ResponseEntity<PmsAdvancedResponse> completeGuestRegistration(
            @PathVariable Long propertyId,
            @PathVariable Long reservationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody CompleteGuestRegistrationRequest request,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.completeGuestRegistration(
                context.company(), propertyId, reservationId, request, context.username(), businessDate));
    }

    @PostMapping("/properties/{propertyId}/reservations/{reservationId}/guest-registration/invite")
    public ResponseEntity<GuestRegistrationInviteResponse> issueGuestRegistrationInvite(
            @PathVariable Long propertyId,
            @PathVariable Long reservationId,
            Principal principal
    ) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.issueGuestRegistrationInvite(
                context.company(), propertyId, reservationId, context.username()));
    }

    private AccessContext requireContext(Principal principal, String accessLevel) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(
                user, UserPermissionService.PAGE_PMS, accessLevel, "Die erforderliche PMS-Berechtigung fehlt.");
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return new AccessContext(user.getCompany(), user.getUsername());
    }

    private record AccessContext(Company company, String username) {
    }
}
