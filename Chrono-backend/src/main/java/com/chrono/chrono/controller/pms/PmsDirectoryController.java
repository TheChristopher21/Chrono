package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsAdvancedResponse.*;
import com.chrono.chrono.dto.pms.PmsDirectoryDtos.Page;
import com.chrono.chrono.services.pms.PmsDirectoryService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController @RequestMapping("/api/pms/properties/{propertyId}/directory")
public class PmsDirectoryController {
 private final PmsDirectoryService service;public PmsDirectoryController(PmsDirectoryService service){this.service=service;}
 @GetMapping("/organizations") public Page<OrganizationView> organizations(@PathVariable Long propertyId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(required=false) String query,@RequestParam(defaultValue="false") boolean activeOnly,@RequestParam(defaultValue="false") boolean masterOnly,Principal p){return service.organizations(p==null?null:p.getName(),propertyId,page,size,query,activeOnly,masterOnly);}
 @GetMapping("/organizations/{id}") public OrganizationView organization(@PathVariable Long propertyId,@PathVariable Long id,Principal p){return service.organization(p==null?null:p.getName(),propertyId,id);}
 @GetMapping("/groups") public Page<GroupBookingView> groups(@PathVariable Long propertyId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(required=false) String query,Principal p){return service.groups(p==null?null:p.getName(),propertyId,page,size,query);}
 @GetMapping("/groups/{id}") public GroupBookingView group(@PathVariable Long propertyId,@PathVariable Long id,Principal p){return service.group(p==null?null:p.getName(),propertyId,id);}
}
