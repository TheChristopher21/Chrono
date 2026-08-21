package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.PmsSetupResponse;
import com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest;
import com.chrono.chrono.dto.pms.UpsertRoomRequest;
import com.chrono.chrono.dto.pms.UpsertRoomTypeRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsSetupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/pms")
public class PmsSetupController {

    private final PmsSetupService pmsSetupService;
    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;

    public PmsSetupController(PmsSetupService pmsSetupService,
                              UserRepository userRepository,
                              UserPermissionService userPermissionService) {
        this.pmsSetupService = pmsSetupService;
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @GetMapping("/setup")
    public ResponseEntity<PmsSetupResponse> getSetup(Principal principal) {
        return ResponseEntity.ok(pmsSetupService.getSetup(requireCompany(principal, UserPermissionService.ACCESS_VIEW)));
    }

    @PostMapping("/properties")
    public ResponseEntity<PmsSetupResponse> createProperty(
            @Valid @RequestBody UpsertHotelPropertyRequest request,
            Principal principal
    ) {
        PmsSetupResponse response = pmsSetupService.createProperty(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                request
        );
        return ResponseEntity.created(URI.create("/api/pms/setup")).body(response);
    }

    @PutMapping("/properties/{propertyId}")
    public ResponseEntity<PmsSetupResponse> updateProperty(
            @PathVariable Long propertyId,
            @Valid @RequestBody UpsertHotelPropertyRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(pmsSetupService.updateProperty(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                propertyId,
                request
        ));
    }

    @PostMapping("/properties/{propertyId}/room-types")
    public ResponseEntity<PmsSetupResponse> createRoomType(
            @PathVariable Long propertyId,
            @Valid @RequestBody UpsertRoomTypeRequest request,
            Principal principal
    ) {
        PmsSetupResponse response = pmsSetupService.createRoomType(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                propertyId,
                request
        );
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/room-types"))
                .body(response);
    }

    @PutMapping("/room-types/{roomTypeId}")
    public ResponseEntity<PmsSetupResponse> updateRoomType(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody UpsertRoomTypeRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(pmsSetupService.updateRoomType(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                roomTypeId,
                request
        ));
    }

    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<PmsSetupResponse> createRoom(
            @PathVariable Long propertyId,
            @Valid @RequestBody UpsertRoomRequest request,
            Principal principal
    ) {
        PmsSetupResponse response = pmsSetupService.createRoom(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                propertyId,
                request
        );
        return ResponseEntity.created(URI.create("/api/pms/properties/" + propertyId + "/rooms"))
                .body(response);
    }

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<PmsSetupResponse> updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody UpsertRoomRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(pmsSetupService.updateRoom(
                requireCompany(principal, UserPermissionService.ACCESS_MANAGE),
                roomId,
                request
        ));
    }

    private Company requireCompany(Principal principal, String accessLevel) {
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
                "Berechtigung für die Hotelverwaltung (PMS) erforderlich."
        );
        if (user.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
        return user.getCompany();
    }
}
