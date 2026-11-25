package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsRateInheritanceDtos.*;
import com.chrono.chrono.services.pms.PmsRateInheritanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/pms/properties/{propertyId}/rate-inheritance")
public class PmsRateInheritanceController {
 private final PmsRateInheritanceService service;
 public record Freeze(boolean frozen,Long expectedVersion) {}
 @GetMapping public List<View> list(@PathVariable Long propertyId,Principal p){return service.list(p.getName(),propertyId);}
 @PutMapping public View save(@PathVariable Long propertyId,@RequestBody Input input,Principal p){return service.save(p.getName(),propertyId,input);}
 @PostMapping("/{id}/apply") public View apply(@PathVariable Long propertyId,@PathVariable Long id,Principal p){return service.apply(p.getName(),propertyId,id);}
 @PatchMapping("/{id}/freeze") public View freeze(@PathVariable Long propertyId,@PathVariable Long id,@RequestBody Freeze input,Principal p){return service.freeze(p.getName(),propertyId,id,input.frozen(),input.expectedVersion());}
}
