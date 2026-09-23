package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.PmsApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/pms/properties/{propertyId}")
public class PmsApprovalController {
    private final PmsApprovalService service;
    @GetMapping("/approval-policy") public PmsApprovalService.Policy policy(@PathVariable Long propertyId,Principal user){return service.policy(user.getName(),propertyId);}
    @PutMapping("/approval-policy") public PmsApprovalService.Policy policy(@PathVariable Long propertyId,@Valid @RequestBody PmsApprovalService.PolicyInput input,Principal user){return service.savePolicy(user.getName(),propertyId,input);}
    @GetMapping("/approvals/refunds") public PmsApprovalService.PageView list(@PathVariable Long propertyId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,
            @RequestParam(defaultValue="OPEN") String status,Principal user){return service.list(user.getName(),propertyId,page,size,status);}
    @PostMapping("/approvals/refunds") public PmsApprovalService.View request(@PathVariable Long propertyId,@Valid @RequestBody PmsApprovalService.Request input,Principal user){return service.request(user.getName(),propertyId,input);}
    @PostMapping("/approvals/refunds/{id}/decision") public PmsApprovalService.View decide(@PathVariable Long propertyId,@PathVariable Long id,@Valid @RequestBody PmsApprovalService.Decision input,Principal user){return service.decide(user.getName(),propertyId,id,input);}
}
