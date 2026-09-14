package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.utils.JwtUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;

/** Renews the ordinary, finite JWT for an authenticated PMS workstation. */
@RestController
@RequestMapping("/api/pms/session")
public class PmsSessionController {
    private final UserRepository userRepository;
    private final UserPermissionService permissionService;
    private final JwtUtil jwtUtil;

    public PmsSessionController(UserRepository userRepository,
                                UserPermissionService permissionService,
                                JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        }
        User user = userRepository.findByUsernameWithPermissionContext(principal.getName())
                .filter(candidate -> !candidate.isDeleted() && !candidate.isDemoExpired(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sitzung nicht mehr gültig."));
        permissionService.assertPageAccess(user, UserPermissionService.PAGE_PMS,
                UserPermissionService.ACCESS_VIEW, "Berechtigung für die Hotelverwaltung (PMS) erforderlich.");
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AuthResponse(jwtUtil.generateTokenWithUser(user)));
    }
}
