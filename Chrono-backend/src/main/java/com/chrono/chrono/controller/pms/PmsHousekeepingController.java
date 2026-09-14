package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsHousekeepingDtos;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsHousekeepingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
@RestController @RequestMapping("/api/pms/properties/{propertyId}/housekeeping/work-orders")
public class PmsHousekeepingController {
    private final PmsHousekeepingService service; private final UserRepository users; private final UserPermissionService permissions;
    public PmsHousekeepingController(PmsHousekeepingService service,UserRepository users,UserPermissionService permissions) { this.service=service;this.users=users;this.permissions=permissions; }
    @GetMapping public List<PmsHousekeepingDtos.Task> list(@PathVariable Long propertyId,@RequestParam(required=false) LocalDate businessDate,Principal principal) { return service.list(company(principal,false),propertyId,businessDate); }
    @PostMapping public PmsHousekeepingDtos.Task create(@PathVariable Long propertyId,@Valid @RequestBody PmsHousekeepingDtos.Create request,Principal principal) { return service.create(company(principal,true),propertyId,request); }
    @PostMapping("/batch") public PmsHousekeepingDtos.BatchResult batch(@PathVariable Long propertyId,@Valid @RequestBody PmsHousekeepingDtos.BatchCreate request,Principal principal) { return service.createBatch(company(principal,true),propertyId,request); }
    @PutMapping("/{taskId}") public PmsHousekeepingDtos.Task update(@PathVariable Long propertyId,@PathVariable Long taskId,@Valid @RequestBody PmsHousekeepingDtos.Update request,Principal principal) { return service.update(company(principal,true),propertyId,taskId,request); }
    @GetMapping("/{taskId}/history") public PmsHousekeepingDtos.History history(@PathVariable Long propertyId,@PathVariable Long taskId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,Principal principal) { return service.history(company(principal,false),propertyId,taskId,page,size); }
    private Company company(Principal principal,boolean write) {
        if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var user=users.findByUsernameWithPermissionContext(principal.getName()).filter(value -> !value.isDeleted()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        permissions.assertPageAccess(user,"pms",write ? "MANAGE" : "VIEW","PMS-Berechtigung fehlt.");
        if(user.getCompany()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return user.getCompany();
    }
}
