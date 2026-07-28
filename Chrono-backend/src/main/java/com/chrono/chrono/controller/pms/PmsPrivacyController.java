package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.AnonymizeGuestRequest;
import com.chrono.chrono.dto.pms.AnonymizeGuestResponse;
import com.chrono.chrono.dto.pms.PmsGuestDataExport;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsPrivacyService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/pms/privacy")
public class PmsPrivacyController {
    private final PmsPrivacyService privacyService;
    private final UserRepository userRepository;
    private final UserPermissionService permissionService;

    public PmsPrivacyController(PmsPrivacyService privacyService,
                                UserRepository userRepository,
                                UserPermissionService permissionService) {
        this.privacyService = privacyService;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @GetMapping("/guests/{guestId}/export")
    public ResponseEntity<PmsGuestDataExport> exportGuest(
            @PathVariable Long guestId,
            Principal principal) {
        AccessContext context = requirePrivacyAdmin(principal);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("pms-gast-" + guestId + "-datenexport.json").build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(privacyService.exportGuestData(
                        context.company(), guestId, context.username()));
    }

    @PostMapping("/guests/{guestId}/anonymize")
    public ResponseEntity<AnonymizeGuestResponse> anonymizeGuest(
            @PathVariable Long guestId,
            @Valid @RequestBody AnonymizeGuestRequest request,
            Principal principal) {
        AccessContext context = requirePrivacyAdmin(principal);
        return ResponseEntity.ok(privacyService.anonymizeGuest(
                context.company(), guestId, request.reason(), context.username()));
    }

    private AccessContext requirePrivacyAdmin(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        permissionService.assertPageAccess(
                user, UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE,
                "PMS-Verwaltungsrecht erforderlich.");
        boolean administrator = user.getRoles().stream().anyMatch(role ->
                "ADMIN".equals(role.getRoleName()) || "SUPERADMIN".equals(role.getRoleName()));
        if (!administrator) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Datenschutzaktionen sind Administratoren vorbehalten.");
        }
        if (user.getCompany() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return new AccessContext(user.getCompany(), user.getUsername());
    }

    private record AccessContext(Company company, String username) {
    }
}
