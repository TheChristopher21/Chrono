package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsHousekeepingDtos;
import com.chrono.chrono.services.pms.PmsHousekeepingCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController @RequestMapping("/api/pms/properties/{propertyId}/housekeeping/offline-commands")
public class PmsHousekeepingCommandController {
    private final PmsHousekeepingCommandService service;
    public PmsHousekeepingCommandController(PmsHousekeepingCommandService service) { this.service=service; }
    @PostMapping public PmsHousekeepingDtos.Task execute(@PathVariable Long propertyId, @Valid @RequestBody PmsHousekeepingCommandService.Command command, Principal principal) { return service.execute(principal == null ? null : principal.getName(),propertyId,command); }
}
