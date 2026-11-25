package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.PmsHistoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.Principal;
@RestController @RequestMapping("/api/pms/properties/{propertyId}/history")
public class PmsHistoryController {
    private final PmsHistoryService history;
    public PmsHistoryController(PmsHistoryService history) { this.history=history; }
    @GetMapping("/{section}") public PmsHistoryService.Result page(@PathVariable Long propertyId,@PathVariable String section,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String query,Principal principal) {
        if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return history.page(principal.getName(),propertyId,section,page,size,query);
    }
}
