package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.PmsExtensionsRequests;
import com.chrono.chrono.dto.pms.PmsExtensionsResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsExtensionsService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/pms")
public class PmsExtensionsController {
    private final PmsExtensionsService service;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsExtensionsController(PmsExtensionsService service, UserRepository userRepository,
                                   UserPermissionService userPermissionService) {
        this.service = service;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping("/extensions")
    public PmsExtensionsResponse get(@RequestParam Long propertyId, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        return service.get(context.company(), propertyId);
    }

    @PutMapping("/properties/{propertyId}/booking-engine")
    public PmsExtensionsResponse updateBookingEngine(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.BookingSettings request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.updateBookingSettings(context.company(), propertyId, request);
    }

    @PutMapping("/properties/{propertyId}/tourism-tax")
    public PmsExtensionsResponse updateTourismTax(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.TourismTaxRuleRequest request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.updateTourismTax(context.company(), propertyId, request);
    }

    @PostMapping("/properties/{propertyId}/tourism-tax/postings")
    public PmsExtensionsResponse postTourismTax(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.PostTourismTax request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.postTourismTax(context.company(), propertyId, request, context.username());
    }

    @PostMapping("/properties/{propertyId}/pos/tickets")
    public PmsExtensionsResponse createPosTicket(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.CreatePosTicket request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.createPosTicket(context.company(), propertyId, request, context.username());
    }

    @PostMapping("/properties/{propertyId}/access-credentials")
    public PmsExtensionsResponse issueAccessCredential(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.IssueAccessCredential request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.issueAccessCredential(context.company(), propertyId, request, context.username());
    }

    @PostMapping("/properties/{propertyId}/access-credentials/{credentialId}/revoke")
    public PmsExtensionsResponse revokeAccessCredential(@PathVariable Long propertyId,
            @PathVariable Long credentialId, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.revokeAccessCredential(context.company(), propertyId, credentialId, context.username());
    }

    @PostMapping("/properties/{propertyId}/migration-batches")
    public PmsExtensionsResponse importMigration(@PathVariable Long propertyId,
            @Valid @RequestBody PmsExtensionsRequests.MigrationImport request, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_MANAGE);
        return service.importMigration(context.company(), propertyId, request, context.username());
    }

    @GetMapping("/properties/{propertyId}/accounting-export.csv")
    public ResponseEntity<byte[]> accountingExport(@PathVariable Long propertyId,
            @RequestParam LocalDate from, @RequestParam LocalDate toExclusive, Principal principal) {
        AccessContext context = requireContext(principal, UserPermissionService.ACCESS_VIEW);
        byte[] content = service.accountingExport(context.company(), propertyId, from, toExclusive);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("chrono-pms-accounting-" + from + ".csv").build().toString())
                .body(content);
    }

    private AccessContext requireContext(Principal principal, String accessLevel) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(user, UserPermissionService.PAGE_PMS, accessLevel,
                "Die erforderliche PMS-Berechtigung fehlt.");
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return new AccessContext(user.getCompany(), user.getUsername());
    }

    private record AccessContext(Company company, String username) {}
}
