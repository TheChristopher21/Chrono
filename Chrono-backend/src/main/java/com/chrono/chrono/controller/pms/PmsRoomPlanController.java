package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.PmsRoomPlanFilter;
import com.chrono.chrono.dto.pms.PmsRoomPlanResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.HousekeepingStatus;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsRoomPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pms/properties/{propertyId}/room-plan")
public class PmsRoomPlanController {
    private final PmsRoomPlanService service;
    private final UserRepository users;
    private final UserPermissionService permissions;

    public PmsRoomPlanController(PmsRoomPlanService service, UserRepository users, UserPermissionService permissions) {
        this.service = service;
        this.users = users;
        this.permissions = permissions;
    }

    @GetMapping
    public PmsRoomPlanResponse get(@PathVariable Long propertyId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String bedType,
            @RequestParam(required = false) String housekeepingSection,
            @RequestParam(required = false) HousekeepingStatus housekeepingStatus,
            @RequestParam(required = false) RoomOperationalStatus operationalStatus,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) List<String> features,
            @RequestParam(defaultValue = "false") boolean onlyAvailable,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        User user = users.findByUsernameWithPermissionContext(principal.getName()).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden."));
        permissions.assertPageAccess(user, UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_VIEW,
                "Die erforderliche PMS-Berechtigung fehlt.");
        if (user.getCompany() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        return service.getRoomPlan(user.getCompany(), propertyId, new PmsRoomPlanFilter(from, days, page, size,
                search, roomTypeId, floor, bedType, housekeepingSection, housekeepingStatus, operationalStatus,
                guests, features, onlyAvailable, includeInactive));
    }
}
