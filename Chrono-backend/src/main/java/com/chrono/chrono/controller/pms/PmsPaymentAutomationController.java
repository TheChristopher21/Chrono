package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.*;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.entities.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
@RestController @RequestMapping("/api/pms/properties/{propertyId}")
public class PmsPaymentAutomationController {
    private final PmsPaymentSettingsService settings;private final PmsPaymentAutomationService automation;private final UserRepository users;private final UserPermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired private PmsLegacyMerchantService legacy;
    public PmsPaymentAutomationController(PmsPaymentSettingsService settings,PmsPaymentAutomationService automation,UserRepository users,UserPermissionService permissions){this.settings=settings;this.automation=automation;this.users=users;this.permissions=permissions;}
    @GetMapping("/payment-settings") public PmsPaymentSettingsService.Settings settings(@PathVariable Long propertyId,Principal principal){var user=user(principal,false);return settings.get(user.getCompany().getId(),propertyId);}
    @PutMapping("/payment-settings") public PmsPaymentSettingsService.Settings save(@PathVariable Long propertyId,@RequestBody PmsPaymentSettingsService.Settings input,Principal principal){var user=user(principal,true);return settings.update(user.getCompany().getId(),propertyId,input,user.getUsername());}
    @GetMapping("/payment-automation") public PmsPaymentAutomationService.Status status(@PathVariable Long propertyId,Principal principal){var user=user(principal,false);return automation.status(user.getCompany().getId(),propertyId);}
    @PostMapping("/payment-settings/legacy-merchant") public void bind(@PathVariable Long propertyId,@RequestBody PmsLegacyMerchantService.Bind input,Principal principal){var user=user(principal,true);legacy.bind(user.getCompany().getId(),propertyId,input,user.getUsername());}
    private User user(Principal principal,boolean master){
        if(principal==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich.");
        User user=users.findByUsernameWithPermissionContext(principal.getName()).filter(u -> !u.isDeleted()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Benutzer nicht gefunden."));
        permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS,master?UserPermissionService.ACCESS_MANAGE:UserPermissionService.ACCESS_VIEW,"PMS-Berechtigung fehlt.");
        if(master)permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS_SETTINGS,UserPermissionService.ACCESS_MANAGE,"Nur Master dürfen Händlerkonten und Automatik ändern.");
        if(user.getCompany()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Firmenzuordnung fehlt.");return user;
    }
}
