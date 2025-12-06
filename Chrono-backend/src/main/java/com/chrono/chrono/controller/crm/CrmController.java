package com.chrono.chrono.controller.crm;

import com.chrono.chrono.dto.crm.*;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.crm.*;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.crm.CrmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private String resolveDisplayName(User user) {
        if (user == null) {
            return null;
        }
        String fullName = Stream.of(user.getFirstName(), user.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getUsername();
    }

    @GetMapping("/customers/{id}/addresses")
    public ResponseEntity<List<CustomerAddressDTO>> listAddresses(@PathVariable Long id, Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        List<CustomerAddressDTO> addresses = crmService.listAddresses(customer).stream()
                .map(CustomerAddressDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(addresses);
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

    @PutMapping("/customers/{id}/addresses/{addressId}")
    public ResponseEntity<CustomerAddressDTO> updateAddress(@PathVariable Long id,
                                                            @PathVariable Long addressId,
                                                            @RequestBody CreateCustomerAddressRequest request,
                                                            Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findAddress(addressId)
                .filter(address -> Objects.equals(address.getCustomer().getId(), customer.getId()))
                .map(address -> crmService.updateAddress(address, request.toEntity()))
                .map(CustomerAddressDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/customers/{id}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id,
                                              @PathVariable Long addressId,
                                              Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findAddress(addressId)
                .filter(address -> Objects.equals(address.getCustomer().getId(), customer.getId()))
                .map(address -> {
                    crmService.deleteAddress(address);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/{id}/documents")
    public ResponseEntity<List<CrmDocumentDTO>> listDocuments(@PathVariable Long id, Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        List<CrmDocumentDTO> documents = crmService.listDocuments(customer).stream()
                .map(CrmDocumentDTO::from)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @PostMapping("/customers/{id}/documents")
    public ResponseEntity<CrmDocumentDTO> addDocument(@PathVariable Long id,
                                                      @RequestBody CreateCrmDocumentRequest request,
                                                      Principal principal) {
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        CrmDocument document = request.toEntity();
        String uploadedBy = resolveDisplayName(user);
        if (uploadedBy != null && !uploadedBy.isBlank()) {
            document.setUploadedBy(uploadedBy);
        }
        CrmDocument saved = crmService.saveDocument(customer, document);
        return ResponseEntity.created(URI.create("/api/crm/customers/" + id + "/documents/" + saved.getId()))
                .body(CrmDocumentDTO.from(saved));
    }

    @DeleteMapping("/customers/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id,
                                               @PathVariable Long documentId,
                                               Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findDocument(documentId)
                .filter(document -> document.getCustomer() != null
                        && Objects.equals(document.getCustomer().getId(), customer.getId()))
                .map(document -> {
                    crmService.deleteDocument(document);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
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

    @PutMapping("/customers/{id}/contacts/{contactId}")
    public ResponseEntity<CustomerContactDTO> updateContact(@PathVariable Long id,
                                                            @PathVariable Long contactId,
                                                            @RequestBody CreateCustomerContactRequest request,
                                                            Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findContact(contactId)
                .filter(contact -> Objects.equals(contact.getCustomer().getId(), customer.getId()))
                .map(contact -> crmService.updateContact(contact, request.toEntity()))
                .map(CustomerContactDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/customers/{id}/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id,
                                              @PathVariable Long contactId,
                                              Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findContact(contactId)
                .filter(contact -> Objects.equals(contact.getCustomer().getId(), customer.getId()))
                .map(contact -> {
                    crmService.deleteContact(contact);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
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

    @PutMapping("/customers/{id}/activities/{activityId}")
    public ResponseEntity<CrmActivityDTO> updateActivity(@PathVariable Long id,
                                                         @PathVariable Long activityId,
                                                         @RequestBody CreateCrmActivityRequest request,
                                                         Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        CustomerContact contact = null;
        if (request.getContactId() != null) {
            contact = crmService.findContact(request.getContactId())
                    .filter(c -> Objects.equals(c.getCustomer().getId(), customer.getId()))
                    .orElse(null);
        }
        CustomerContact finalContact = contact;
        return crmService.findActivity(activityId)
                .filter(activity -> Objects.equals(activity.getCustomer().getId(), customer.getId()))
                .map(existing -> {
                    CrmActivity changes = request.toEntity();
                    changes.setCustomer(customer);
                    changes.setContact(finalContact);
                    if (finalContact != null) {
                        changes.setContact(finalContact);
                    }
                    CrmActivity updated = crmService.updateActivity(existing, changes);
                    return ResponseEntity.ok(CrmActivityDTO.from(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/customers/{id}/activities/{activityId}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id,
                                               @PathVariable Long activityId,
                                               Principal principal) {
        User user = getUser(principal);
        Customer customer = customerService.findById(id).orElse(null);
        if (customer == null || !allowed(user, customer)) {
            return user == null ? ResponseEntity.status(401).build() : ResponseEntity.status(403).build();
        }
        return crmService.findActivity(activityId)
                .filter(activity -> Objects.equals(activity.getCustomer().getId(), customer.getId()))
                .map(activity -> {
                    crmService.deleteActivity(activity);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<List<CrmLeadDTO>> listLeads(@RequestParam(required = false) LeadStatus status, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<CrmLeadDTO> leads = crmService.getLeads(user.getCompany(), status).stream()
                .map(CrmLeadDTO::from)
                .toList();
        return ResponseEntity.ok(leads);
    }

    @PatchMapping("/leads/{id}")
    public ResponseEntity<CrmLeadDTO> updateLead(@PathVariable Long id,
                                                 @RequestBody UpdateLeadStatusRequest request,
                                                 Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        return crmService.findLead(id)
                .filter(lead -> Objects.equals(lead.getCompany().getId(), user.getCompany().getId()))
                .map(lead -> crmService.updateLeadStatus(lead, request.getStatus()))
                .map(CrmLeadDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<List<OpportunityDTO>> listOpportunities(@RequestParam(required = false) OpportunityStage stage, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<OpportunityDTO> opportunities = crmService.getOpportunities(user.getCompany(), stage).stream()
                .map(OpportunityDTO::from)
                .toList();
        return ResponseEntity.ok(opportunities);
    }

    @PatchMapping("/opportunities/{id}")
    public ResponseEntity<OpportunityDTO> updateOpportunity(@PathVariable Long id,
                                                             @RequestBody UpdateOpportunityRequest request,
                                                             Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        return crmService.findOpportunity(id)
                .filter(opportunity -> Objects.equals(opportunity.getCompany().getId(), user.getCompany().getId()))
                .map(opportunity -> crmService.updateOpportunity(opportunity, request.getStage(),
                        request.getProbability(), request.getExpectedCloseDate()))
                .map(OpportunityDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<List<CampaignDTO>> listCampaigns(@RequestParam(required = false) CampaignStatus status, Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        List<CampaignDTO> campaigns = crmService.getCampaigns(user.getCompany(), status).stream()
                .map(CampaignDTO::from)
                .toList();
        return ResponseEntity.ok(campaigns);
    }

    @PatchMapping("/campaigns/{id}")
    public ResponseEntity<CampaignDTO> updateCampaign(@PathVariable Long id,
                                                      @RequestBody UpdateCampaignRequest request,
                                                      Principal principal) {
        User user = getUser(principal);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.status(401).build();
        }
        return crmService.findCampaign(id)
                .filter(campaign -> Objects.equals(campaign.getCompany().getId(), user.getCompany().getId()))
                .map(campaign -> crmService.updateCampaign(campaign, request.getStatus(), request.getName(),
                        request.getStartDate(), request.getEndDate(), request.getBudget(), request.getChannel()))
                .map(CampaignDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
