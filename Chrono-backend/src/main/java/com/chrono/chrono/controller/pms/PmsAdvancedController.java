package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsReportingService;
import com.chrono.chrono.services.pms.PmsReceivablesService;
import com.chrono.chrono.services.pms.PmsGroupService;
import com.chrono.chrono.services.pms.PmsEventOrderService;
import com.chrono.chrono.services.pms.PmsAccountingSettingsService;
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
    private final PmsReceivablesService receivables;
    private final PmsGroupService groupOperations;
    private final PmsEventOrderService eventOrders;
    private final PmsAccountingSettingsService accountingSettings;

    public PmsAdvancedController(PmsAdvancedService advancedService,
                                 PmsReportingService reportingService,
                                 UserRepository userRepository,
                                 UserPermissionService userPermissionService, PmsReceivablesService receivables,PmsGroupService groupOperations,PmsEventOrderService eventOrders,
                                 PmsAccountingSettingsService accountingSettings) {
        this.advancedService = advancedService;
        this.reportingService = reportingService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
        this.receivables = receivables;
        this.groupOperations = groupOperations;
        this.eventOrders = eventOrders;
        this.accountingSettings = accountingSettings;
    }

    @GetMapping("/properties/{propertyId}/accounting-settings")
    public ResponseEntity<PmsAccountingSettingsDto> accountingSettings(@PathVariable Long propertyId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(accountingSettings.get(context.company(),propertyId));
    }
    @PutMapping("/properties/{propertyId}/accounting-settings")
    public ResponseEntity<PmsAccountingSettingsDto> saveAccountingSettings(@PathVariable Long propertyId,
            @Valid @RequestBody PmsAccountingSettingsDto request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE,true);
        return ResponseEntity.ok(accountingSettings.update(context.company(),propertyId,request,context.username()));
    }

    @GetMapping("/properties/{propertyId}/resource-bookings/{bookingId}/event-order")
    public ResponseEntity<PmsEventOrderDto.View> eventOrder(@PathVariable Long propertyId,@PathVariable Long bookingId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(eventOrders.get(context.company(),propertyId,bookingId));
    }
    @PutMapping("/properties/{propertyId}/resource-bookings/{bookingId}/event-order")
    public ResponseEntity<PmsEventOrderDto.View> saveEventOrder(@PathVariable Long propertyId,@PathVariable Long bookingId,
            @Valid @RequestBody PmsEventOrderDto.Save request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(eventOrders.save(context.company(),propertyId,bookingId,request));
    }
    @PostMapping("/properties/{propertyId}/resource-bookings/{bookingId}/event-order/post")
    public ResponseEntity<PmsEventOrderDto.View> postEventOrder(@PathVariable Long propertyId,@PathVariable Long bookingId,
            @Valid @RequestBody PmsEventOrderDto.Post request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(eventOrders.post(context.company(),propertyId,bookingId,request,context.username()));
    }
    @GetMapping("/properties/{propertyId}/resource-bookings/{bookingId}/event-order/beo.pdf")
    public ResponseEntity<byte[]> eventOrderPdf(@PathVariable Long propertyId,@PathVariable Long bookingId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=event-order-"+bookingId+".pdf").body(eventOrders.beoPdf(context.company(),propertyId,bookingId));
    }

    @GetMapping("/properties/{propertyId}/groups/{groupId}/operations")
    public ResponseEntity<PmsGroupOperationsDto.View> groupOperations(@PathVariable Long propertyId,@PathVariable Long groupId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(groupOperations.view(context.company(),propertyId,groupId));
    }
    @PostMapping("/properties/{propertyId}/groups/{groupId}/allotments")
    public ResponseEntity<PmsGroupOperationsDto.View> groupAllotment(@PathVariable Long propertyId,@PathVariable Long groupId,
            @Valid @RequestBody PmsGroupOperationsDto.Allotment request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(groupOperations.addAllotment(context.company(),propertyId,groupId,request));
    }
    @PostMapping("/properties/{propertyId}/groups/{groupId}/allotments/{allotmentId}/release")
    public ResponseEntity<PmsGroupOperationsDto.View> releaseAllotment(@PathVariable Long propertyId,@PathVariable Long groupId,
            @PathVariable Long allotmentId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(groupOperations.release(context.company(),propertyId,groupId,allotmentId));
    }
    @PostMapping("/properties/{propertyId}/groups/{groupId}/rooming-list")
    public ResponseEntity<PmsGroupOperationsDto.View> appendRoomingList(@PathVariable Long propertyId,@PathVariable Long groupId,
            @Valid @RequestBody PmsGroupOperationsDto.RoomingList request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(groupOperations.appendMembers(context.company(),propertyId,groupId,request,context.username()));
    }
    @PutMapping("/properties/{propertyId}/groups/{groupId}/routing")
    public ResponseEntity<PmsGroupOperationsDto.View> groupRouting(@PathVariable Long propertyId,@PathVariable Long groupId,
            @Valid @RequestBody PmsGroupOperationsDto.Routing request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(groupOperations.configureRouting(context.company(),propertyId,groupId,request));
    }
    @PostMapping("/properties/{propertyId}/groups/{groupId}/bulk-operation")
    public ResponseEntity<java.util.List<PmsGroupOperationsDto.MemberResult>> bulkGroupOperation(@PathVariable Long propertyId,@PathVariable Long groupId,
            @Valid @RequestBody PmsGroupOperationsDto.BulkOperation request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(groupOperations.bulk(context.company(),propertyId,groupId,request,context.username()));
    }

    @GetMapping("/properties/{propertyId}/credit-accounts")
    public ResponseEntity<java.util.List<PmsReceivablesDto.CreditView>> creditAccounts(@PathVariable Long propertyId,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(receivables.accounts(context.company(),propertyId));
    }

    @PutMapping("/properties/{propertyId}/organizations/{organizationId}/credit-account")
    public ResponseEntity<PmsReceivablesDto.CreditView> configureCredit(@PathVariable Long propertyId,@PathVariable Long organizationId,
            @Valid @RequestBody PmsReceivablesDto.CreditSettings request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE,true);
        return ResponseEntity.ok(receivables.configure(context.company(),propertyId,organizationId,request,context.username()));
    }

    @PostMapping("/properties/{propertyId}/invoices/{invoiceId}/direct-bill")
    public ResponseEntity<PmsReceivablesDto.ReceivableView> directBill(@PathVariable Long propertyId,@PathVariable Long invoiceId,
            @Valid @RequestBody PmsReceivablesDto.DirectBill request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(receivables.directBill(context.company(),propertyId,invoiceId,request,context.username()));
    }

    @GetMapping("/properties/{propertyId}/receivables")
    public ResponseEntity<PmsReceivablesDto.Page> receivables(@PathVariable Long propertyId,
            @RequestParam(required=false) Long organizationId,@RequestParam(defaultValue="false") boolean openOnly,
            @RequestParam(defaultValue="false") boolean overdueOnly,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size,@RequestParam(required=false) String query,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(receivables.listPage(context.company(),propertyId,organizationId,openOnly,overdueOnly,page,size,query));
    }

    @PostMapping("/properties/{propertyId}/receivables/{receivableId}/settlements")
    public ResponseEntity<PmsReceivablesDto.ReceivableView> settleReceivable(@PathVariable Long propertyId,@PathVariable Long receivableId,
            @Valid @RequestBody PmsReceivablesDto.Settlement request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(receivables.settle(context.company(),propertyId,receivableId,request,context.username(),false));
    }

    @PostMapping("/properties/{propertyId}/receivables/{receivableId}/refunds")
    public ResponseEntity<PmsReceivablesDto.ReceivableView> refundReceivable(@PathVariable Long propertyId,@PathVariable Long receivableId,
            @Valid @RequestBody PmsReceivablesDto.Settlement request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(receivables.settle(context.company(),propertyId,receivableId,request,context.username(),true));
    }

    @PostMapping("/properties/{propertyId}/receivables/{receivableId}/reminders")
    public ResponseEntity<PmsReceivablesDto.ReceivableView> remindReceivable(@PathVariable Long propertyId,@PathVariable Long receivableId,
            @Valid @RequestBody PmsReceivablesDto.Reminder request,Principal principal) {
        AccessContext context=requireContext(principal,UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(receivables.remind(context.company(),propertyId,receivableId,request,context.username()));
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

    @GetMapping("/properties/{propertyId}/financial-day")
    public ResponseEntity<PmsFinancialDayResponse> financialDay(@PathVariable Long propertyId, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(advancedService.financialDay(context.company(), propertyId));
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
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE, true);
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

    @PostMapping("/properties/{propertyId}/organizations/{sourceOrganizationId}/merge")
    public ResponseEntity<PmsAdvancedResponse> mergeOrganization(
            @PathVariable Long propertyId,
            @PathVariable Long sourceOrganizationId,
            @RequestParam(required = false) LocalDate businessDate,
            @Valid @RequestBody MergeOrganizationsRequest request,
            Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return ResponseEntity.ok(advancedService.mergeOrganization(
                context.company(), propertyId, sourceOrganizationId, request, businessDate));
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
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE, true);
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
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE, true);
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
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE, true);
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
        return requireContext(principal, accessLevel, false);
    }

    private AccessContext requireContext(Principal principal, String accessLevel, boolean masterOnly) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(
                user, UserPermissionService.PAGE_PMS, accessLevel, "Die erforderliche PMS-Berechtigung fehlt.");
        if (masterOnly) {
            userPermissionService.assertPageAccess(user, UserPermissionService.PAGE_PMS_SETTINGS,
                    UserPermissionService.ACCESS_MANAGE, "Für diese Einstellung ist ein PMS-Master erforderlich.");
        }
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return new AccessContext(user.getCompany(), user.getUsername());
    }

    private record AccessContext(Company company, String username) {
    }
}
