package com.chrono.chrono.controller.crm;

import com.chrono.chrono.dto.crm.*;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.crm.*;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.crm.CrmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crmService;
    private final CustomerService customerService;
    private final UserService userService;

    public CrmController(CrmService crmService,
                         CustomerService customerService,
                         UserService userService) {
        this.crmService = crmService;
        this.customerService = customerService;
        this.userService = userService;
    }

    private User getUser(Principal principal) {
        return principal != null ? userService.getUserByUsername(principal.getName()) : null;
    }

    private boolean allowed(User user, Customer customer) {
        return user != null && user.getCompany() != null && customer.getCompany() != null
                && Objects.equals(user.getCompany().getId(), customer.getCompany().getId());
    }

    @PostMapping("/customers/{id}/addresses")
    public ResponseEntity<CustomerAddressDTO> addAddress(@PathVariable Long id,
                                                         @RequestBody CreateCustomerAddressRequest request,
                                                         Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        CustomerAddress saved = crmService.addAddress(customer, request.toEntity());
        return ResponseEntity.created(URI.create("/api/crm/customers/" + id + "/addresses/" + saved.getId()))
                .body(CustomerAddressDTO.from(saved));
    }

    @PostMapping("/customers/{id}/contacts")
    public ResponseEntity<CustomerContactDTO> addContact(@PathVariable Long id,
                                                         @RequestBody CreateCustomerContactRequest request,
                                                         Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        CustomerContact saved = crmService.addContact(customer, request.toEntity());
        return ResponseEntity.created(URI.create("/api/crm/customers/" + id + "/contacts/" + saved.getId()))
                .body(CustomerContactDTO.from(saved));
    }

    @GetMapping("/customers/{id}/contacts")
    public ResponseEntity<List<CustomerContactDTO>> listContacts(@PathVariable Long id, Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        List<CustomerContactDTO> contacts = crmService.listContacts(customer).stream()
                .map(CustomerContactDTO::from)
                .toList();
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/customers/{id}/activities")
    public ResponseEntity<List<CrmActivityDTO>> listActivities(@PathVariable Long id, Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        List<CrmActivityDTO> activities = crmService.listActivities(customer).stream()
                .map(CrmActivityDTO::from)
                .toList();
        return ResponseEntity.ok(activities);
    }

    @PostMapping("/customers/{id}/activities")
    public ResponseEntity<CrmActivityDTO> addActivity(@PathVariable Long id,
                                                      @RequestBody CreateCrmActivityRequest request,
                                                      Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        CustomerContact contact = null;
        if (request.getContactId() != null) {
            contact = crmService.listContacts(customer).stream()
                    .filter(c -> Objects.equals(c.getId(), request.getContactId()))
                    .findFirst().orElse(null);
        }
        CrmActivity activity = request.toEntity();
        activity.setOwner(user != null ? user.getUsername() : null);
        CrmActivity saved = crmService.logActivity(customer, contact, activity);
        return ResponseEntity.created(URI.create("/api/crm/customers/" + id + "/activities/" + saved.getId()))
                .body(CrmActivityDTO.from(saved));
    }

    @PostMapping("/leads")
    public ResponseEntity<CrmLeadDTO> createLead(@RequestBody CreateCrmLeadRequest request, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        CrmLead lead = request.toEntity();
        lead.setCompany(user.getCompany());
        CrmLead saved = crmService.saveLead(lead);
        return ResponseEntity.created(URI.create("/api/crm/leads/" + saved.getId()))
                .body(CrmLeadDTO.from(saved));
    }

    @GetMapping("/leads")
    public ResponseEntity<List<CrmLeadDTO>> listLeads(@RequestParam LeadStatus status, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<CrmLeadDTO> leads = crmService.getLeadsByStatus(user.getCompany(), status).stream()
                .map(CrmLeadDTO::from)
                .toList();
        return ResponseEntity.ok(leads);
    }

    @PostMapping("/opportunities")
    public ResponseEntity<OpportunityDTO> createOpportunity(@RequestBody CreateOpportunityRequest request, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        Opportunity opportunity = request.toEntity();
        opportunity.setCompany(user.getCompany());
        if (request.getCustomerId() != null) {
            Customer customer = customerService.findById(request.getCustomerId()).orElse(null);
            if (customer == null || !allowed(user, customer)) {
                return ResponseEntity.status(403).build();
            }
            opportunity.setCustomer(customer);
        }
        if (opportunity.getCustomer() != null && !allowed(user, opportunity.getCustomer())) {
            return ResponseEntity.status(403).build();
        }
        Opportunity saved = crmService.saveOpportunity(opportunity);
        return ResponseEntity.created(URI.create("/api/crm/opportunities/" + saved.getId()))
                .body(OpportunityDTO.from(saved));
    }

    @GetMapping("/opportunities")
    public ResponseEntity<List<OpportunityDTO>> listOpportunities(@RequestParam OpportunityStage stage, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<OpportunityDTO> opportunities = crmService.getOpportunitiesByStage(user.getCompany(), stage).stream()
                .map(OpportunityDTO::from)
                .toList();
        return ResponseEntity.ok(opportunities);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<CampaignDTO> createCampaign(@RequestBody CreateCampaignRequest request, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        Campaign campaign = request.toEntity();
        campaign.setCompany(user.getCompany());
        Campaign saved = crmService.saveCampaign(campaign);
        return ResponseEntity.created(URI.create("/api/crm/campaigns/" + saved.getId()))
                .body(CampaignDTO.from(saved));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDTO>> listCampaigns(@RequestParam CampaignStatus status, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<CampaignDTO> campaigns = crmService.getCampaignsByStatus(user.getCompany(), status).stream()
                .map(CampaignDTO::from)
                .toList();
        return ResponseEntity.ok(campaigns);
    }
}
