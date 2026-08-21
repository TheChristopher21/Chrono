package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsOperationalHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/pms")
public class PmsMonitoringController {
    private final PmsOperationalHealthService healthService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsMonitoringController(PmsOperationalHealthService healthService,
                                   UserRepository userRepository,
                                   UserPermissionService userPermissionService) {
        this.healthService = healthService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping("/health")
    public ResponseEntity<PmsOperationalHealthResponse> health(
            @RequestParam Long propertyId,
            Principal principal) {
        return ResponseEntity.ok(healthService.health(requireCompany(principal), propertyId));
    }

    private Company requireCompany(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        userPermissionService.assertPageAccess(
                user, UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW,
                "Die erforderliche PMS-Berechtigung fehlt.");
        if (user.getCompany() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return user.getCompany();
    }
}
