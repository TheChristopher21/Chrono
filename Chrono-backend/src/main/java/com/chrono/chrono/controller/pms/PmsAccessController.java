package com.chrono.chrono.controller.pms;

import com.chrono.chrono.dto.pms.PmsAccessDtos;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/pms/access")
public class PmsAccessController {
    private final PmsPropertyAccessService service;
    public PmsAccessController(PmsPropertyAccessService service) { this.service = service; }
    @GetMapping("/me") public PmsAccessDtos.Self self(Principal principal) { return service.self(principal == null ? null : principal.getName()); }
    @GetMapping("/users") public PmsAccessDtos.Administration users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="") String query,Principal principal) { return service.administration(principal == null ? null : principal.getName(),page,size,query); }
    @PutMapping("/users/{userId}") public PmsAccessDtos.UserAccess update(@PathVariable Long userId, @Valid @RequestBody PmsAccessDtos.Update request, Principal principal) {
        return service.update(principal == null ? null : principal.getName(), userId, request);
    }
}
