package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsRevenueAutomationDtos.*;
import com.chrono.chrono.services.pms.PmsRevenueAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor @RequestMapping("/api/pms/properties/{propertyId}/reports/revenue-planning")
public class PmsRevenueAutomationController {
 private final PmsRevenueAutomationService service;
 @GetMapping("/automation") public Config config(@PathVariable Long propertyId,Principal p){return service.config(p.getName(),propertyId);}
 @PutMapping("/automation") public Config save(@PathVariable Long propertyId,@RequestBody Config body,Principal p){return service.save(p.getName(),propertyId,body);}
 @GetMapping("/forecast") public Forecast forecast(@PathVariable Long propertyId,@RequestParam(required=false) LocalDate from,@RequestParam(defaultValue="30") int days,Principal p){return service.forecast(p.getName(),propertyId,from,days);}
 @PostMapping("/pricing/apply") public void apply(@PathVariable Long propertyId,@RequestBody Apply body,Principal p){service.apply(p.getName(),propertyId,body);}
}
