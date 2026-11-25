package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsRatePublicationDtos.*;
import com.chrono.chrono.services.pms.PmsRatePublicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
@RestController
@RequestMapping("/api/pms/rate-plans")
public class PmsRatePublicationController {
    private final PmsRatePublicationService service;
    public PmsRatePublicationController(PmsRatePublicationService service) { this.service=service; }
    @PostMapping("/{sourceRatePlanId}/publish")
    public Published publish(@PathVariable Long sourceRatePlanId,@Valid @RequestBody Publish request,Principal principal) {
        if(principal==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentifizierung erforderlich.");
        return service.publish(principal.getName(),sourceRatePlanId,request);
    }
}
