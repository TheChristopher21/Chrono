package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsRevenuePlanningDtos.*;
import com.chrono.chrono.services.pms.PmsRevenuePlanningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.LocalDate;
@RestController
@RequestMapping("/api/pms/properties/{propertyId}/reports/revenue-planning")
public class PmsRevenuePlanningController {
 private final PmsRevenuePlanningService service;
 public PmsRevenuePlanningController(PmsRevenuePlanningService service){this.service=service;}
 @GetMapping public Report report(@PathVariable Long propertyId,@RequestParam(required=false) LocalDate from,
         @RequestParam(required=false,name="to") LocalDate to,@RequestParam(required=false) Long comparisonSnapshotId,Principal principal){return service.report(username(principal),propertyId,from,to,comparisonSnapshotId);}
 @PostMapping("/snapshots") public SnapshotView capture(@PathVariable Long propertyId,Principal principal){return service.capture(username(principal),propertyId);}
 @PutMapping("/budgets/{month}") public BudgetView budget(@PathVariable Long propertyId,@PathVariable String month,@Valid @RequestBody BudgetInput request,Principal principal){return service.saveBudget(username(principal),propertyId,month,request);}
 private String username(Principal principal){if(principal==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentifizierung erforderlich.");return principal.getName();}
}
