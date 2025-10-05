package com.chrono.chrono.services.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.*;
import com.chrono.chrono.repositories.crm.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CrmService {

    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerContactRepository customerContactRepository;
    private final CrmActivityRepository crmActivityRepository;
    private final CrmLeadRepository crmLeadRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final CrmDocumentRepository crmDocumentRepository;

    public CrmService(CustomerAddressRepository customerAddressRepository,
                      CustomerContactRepository customerContactRepository,
                      CrmActivityRepository crmActivityRepository,
                      CrmLeadRepository crmLeadRepository,
                      OpportunityRepository opportunityRepository,
                      CampaignRepository campaignRepository,
                      CrmDocumentRepository crmDocumentRepository) {
        this.customerAddressRepository = customerAddressRepository;
        this.customerContactRepository = customerContactRepository;
        this.crmActivityRepository = crmActivityRepository;
        this.crmLeadRepository = crmLeadRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.crmDocumentRepository = crmDocumentRepository;
    }

    @Transactional
    public CustomerAddress addAddress(Customer customer, CustomerAddress address) {
        address.setCustomer(customer);
        return customerAddressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> listAddresses(Customer customer) {
        return customerAddressRepository.findByCustomer(customer);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerAddress> findAddress(Long id) {
        return customerAddressRepository.findById(id);
    }

    @Transactional
    public CustomerAddress updateAddress(CustomerAddress existing, CustomerAddress changes) {
        existing.setStreet(changes.getStreet());
        existing.setPostalCode(changes.getPostalCode());
        existing.setCity(changes.getCity());
        existing.setCountry(changes.getCountry());
        existing.setType(changes.getType());
        return customerAddressRepository.save(existing);
    }

    @Transactional
    public void deleteAddress(CustomerAddress address) {
        customerAddressRepository.delete(address);
    }

    @Transactional
    public CustomerContact addContact(Customer customer, CustomerContact contact) {
        contact.setCustomer(customer);
        return customerContactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerContact> findContact(Long id) {
        return customerContactRepository.findById(id);
    }

    @Transactional
    public CustomerContact updateContact(CustomerContact contact, CustomerContact changes) {
        contact.setFirstName(changes.getFirstName());
        contact.setLastName(changes.getLastName());
        contact.setEmail(changes.getEmail());
        contact.setPhone(changes.getPhone());
        contact.setRoleTitle(changes.getRoleTitle());
        return customerContactRepository.save(contact);
    }

    @Transactional
    public void deleteContact(CustomerContact contact) {
        customerContactRepository.delete(contact);
    }

    @Transactional
    public CrmActivity logActivity(Customer customer, CustomerContact contact, CrmActivity activity) {
        activity.setCustomer(customer);
        activity.setContact(contact);
        return crmActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public Optional<CrmActivity> findActivity(Long id) {
        return crmActivityRepository.findById(id);
    }

    @Transactional
    public CrmActivity updateActivity(CrmActivity activity, CrmActivity changes) {
        activity.setType(changes.getType());
        activity.setNotes(changes.getNotes());
        if (changes.getTimestamp() != null) {
            activity.setTimestamp(changes.getTimestamp());
        }
        activity.setContact(changes.getContact());
        return crmActivityRepository.save(activity);
    }

    @Transactional
    public void deleteActivity(CrmActivity activity) {
        crmActivityRepository.delete(activity);
    }

    @Transactional
    public CrmLead saveLead(CrmLead lead) {
        return crmLeadRepository.save(lead);
    }

    @Transactional(readOnly = true)
    public Optional<CrmLead> findLead(Long id) {
        return crmLeadRepository.findById(id);
    }

    @Transactional
    public CrmLead updateLeadStatus(CrmLead lead, LeadStatus status) {
        lead.setStatus(status);
        return crmLeadRepository.save(lead);
    }

    @Transactional
    public Opportunity saveOpportunity(Opportunity opportunity) {
        return opportunityRepository.save(opportunity);
    }

    @Transactional(readOnly = true)
    public Optional<Opportunity> findOpportunity(Long id) {
        return opportunityRepository.findById(id);
    }

    @Transactional
    public Opportunity updateOpportunity(Opportunity opportunity, OpportunityStage stage, Double probability,
                                         LocalDate expectedCloseDate) {
        if (stage != null) {
            opportunity.setStage(stage);
        }
        if (probability != null) {
            opportunity.setProbability(probability);
        }
        if (expectedCloseDate != null) {
            opportunity.setExpectedCloseDate(expectedCloseDate);
        }
        return opportunityRepository.save(opportunity);
    }

    @Transactional
    public Campaign saveCampaign(Campaign campaign) {
        return campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public Optional<Campaign> findCampaign(Long id) {
        return campaignRepository.findById(id);
    }

    @Transactional
    public Campaign updateCampaign(Campaign campaign, CampaignStatus status, String name,
                                   LocalDate startDate, LocalDate endDate, Integer budget, String channel) {
        if (status != null) {
            campaign.setStatus(status);
        }
        if (name != null) {
            campaign.setName(name);
        }
        if (startDate != null) {
            campaign.setStartDate(startDate);
        }
        if (endDate != null) {
            campaign.setEndDate(endDate);
        }
        if (budget != null) {
            campaign.setBudget(budget);
        }
        if (channel != null) {
            campaign.setChannel(channel);
        }
        return campaignRepository.save(campaign);
    }

    @Transactional
    public CrmDocument saveDocument(Customer customer, CrmDocument document) {
        document.setCustomer(customer);
        return crmDocumentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<CrmLead> getLeadsByStatus(Company company, LeadStatus status) {
        return crmLeadRepository.findByCompanyAndStatus(company, status);
    }

    @Transactional(readOnly = true)
    public List<Opportunity> getOpportunitiesByStage(Company company, OpportunityStage stage) {
        return opportunityRepository.findByCompanyAndStage(company, stage);
    }

    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsByStatus(Company company, CampaignStatus status) {
        return campaignRepository.findByCompanyAndStatus(company, status);
    }

    @Transactional(readOnly = true)
    public List<CustomerContact> listContacts(Customer customer) {
        return customerContactRepository.findByCustomer(customer);
    }

    @Transactional(readOnly = true)
    public List<CrmActivity> listActivities(Customer customer) {
        return crmActivityRepository.findByCustomerOrderByTimestampDesc(customer);
    }

    @Transactional(readOnly = true)
    public List<CrmDocument> listDocuments(Customer customer) {
        return crmDocumentRepository.findByCustomerOrderByUploadedAtDesc(customer);
    }
}
