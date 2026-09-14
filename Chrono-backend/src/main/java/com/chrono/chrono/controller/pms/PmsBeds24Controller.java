package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.PmsBeds24Service;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.entities.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;import java.time.LocalDate;import java.util.List;
@RestController @RequestMapping("/api/pms/properties/{propertyId}/beds24")
public class PmsBeds24Controller {
 private final PmsBeds24Service service;private final UserRepository users;private final UserPermissionService permissions;
 public PmsBeds24Controller(PmsBeds24Service service,UserRepository users,UserPermissionService permissions){this.service=service;this.users=users;this.permissions=permissions;}
 @GetMapping public PmsBeds24Service.View get(@PathVariable Long propertyId,Principal principal){var user=user(principal,false,false);return service.get(user.getCompany().getId(),propertyId);}
 @PutMapping("/settings") public PmsBeds24Service.View save(@PathVariable Long propertyId,@RequestBody PmsBeds24Service.Save input,Principal principal){var user=user(principal,true,true);return service.save(user.getCompany().getId(),propertyId,input,user.getUsername());}
 @PostMapping("/publications") public PmsBeds24Service.Job publish(@PathVariable Long propertyId,@RequestBody PmsBeds24Service.Publish input,Principal principal){var user=user(principal,true,false);return service.publish(user.getCompany().getId(),propertyId,input,user.getUsername());}
 @PostMapping("/publications/{id}/retry") public PmsBeds24Service.Job retry(@PathVariable Long propertyId,@PathVariable Long id,Principal principal){var user=user(principal,true,false);return service.retry(user.getCompany().getId(),propertyId,id,false,user.getUsername());}
 @PostMapping("/publications/{id}/discard") public PmsBeds24Service.Job discard(@PathVariable Long propertyId,@PathVariable Long id,Principal principal){var user=user(principal,true,false);return service.retry(user.getCompany().getId(),propertyId,id,true,user.getUsername());}
 @GetMapping("/reconciliation") public List<PmsBeds24Service.Reconciliation> reconcile(@PathVariable Long propertyId,@RequestParam LocalDate from,@RequestParam LocalDate to,Principal principal){var user=user(principal,false,false);return service.reconcile(user.getCompany().getId(),propertyId,from,to);}
 @PostMapping("/bookings/{bookingId}/link") public PmsBeds24Service.Reconciliation link(@PathVariable Long propertyId,@PathVariable Long bookingId,@RequestBody PmsBeds24Service.Link input,Principal principal){var user=user(principal,true,false);return service.link(user.getCompany().getId(),propertyId,bookingId,input,user.getUsername());}
 private User user(Principal principal,boolean manage,boolean master){if(principal==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich.");User user=users.findByUsernameWithPermissionContext(principal.getName()).filter(u -> !u.isDeleted()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Benutzer nicht gefunden."));permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS,manage?UserPermissionService.ACCESS_MANAGE:UserPermissionService.ACCESS_VIEW,"PMS-Berechtigung fehlt.");if(master)permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS_SETTINGS,UserPermissionService.ACCESS_MANAGE,"Nur Master dürfen die Anbieterzuordnung ändern.");if(user.getCompany()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Firmenzuordnung fehlt.");return user;}
}
