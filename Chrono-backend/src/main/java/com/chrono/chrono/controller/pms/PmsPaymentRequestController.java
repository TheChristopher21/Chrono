package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsPaymentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/pms/properties/{propertyId}")
public class PmsPaymentRequestController {
    private final PmsPaymentRequestService payments;
    private final UserRepository users;
    private final UserPermissionService permissions;
    public PmsPaymentRequestController(PmsPaymentRequestService payments, UserRepository users, UserPermissionService permissions) {
        this.payments = payments; this.users = users; this.permissions = permissions;
    }
    @GetMapping("/folios/{folioId}/payment-requests")
    public List<PmsPaymentRequestView> list(@PathVariable Long propertyId, @PathVariable Long folioId, Principal principal) {
        User user = user(principal, UserPermissionService.ACCESS_VIEW);
        return payments.list(user.getCompany().getId(), propertyId, folioId);
    }
    @PostMapping("/folios/{folioId}/payment-requests")
    public PmsPaymentRequestView create(@PathVariable Long propertyId, @PathVariable Long folioId,
            @Valid @RequestBody CreatePmsPaymentRequest request, Principal principal) {
        User user = user(principal, UserPermissionService.ACCESS_MANAGE);
        return payments.create(user.getCompany().getId(), propertyId, folioId, request, user.getUsername());
    }
    @PostMapping("/payment-requests/{id}/reconcile")
    public PmsPaymentRequestView reconcile(@PathVariable Long propertyId, @PathVariable Long id, Principal principal) {
        User user = user(principal, UserPermissionService.ACCESS_MANAGE);
        return payments.reconcile(user.getCompany().getId(), propertyId, id, user.getUsername());
    }
    @PostMapping("/payment-requests/{id}/capture")
    public PmsPaymentRequestView capture(@PathVariable Long propertyId, @PathVariable Long id,
            @RequestBody(required = false) CaptureRequest request, Principal principal) {
        User user = user(principal, UserPermissionService.ACCESS_MANAGE);
        return payments.capture(user.getCompany().getId(), propertyId, id, request == null ? null : request.amount(), user.getUsername());
    }
    @PostMapping("/payment-requests/{id}/release")
    public PmsPaymentRequestView release(@PathVariable Long propertyId, @PathVariable Long id, Principal principal) {
        User user = user(principal, UserPermissionService.ACCESS_MANAGE);
        return payments.release(user.getCompany().getId(), propertyId, id, user.getUsername());
    }
    public record CaptureRequest(BigDecimal amount) {}
    private User user(Principal principal, String access) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        User user = users.findByUsernameWithPermissionContext(principal.getName()).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        permissions.assertPageAccess(user, UserPermissionService.PAGE_PMS, access, "Die erforderliche PMS-Berechtigung fehlt.");
        if (user.getCompany() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        return user;
    }
}
