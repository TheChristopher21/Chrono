package com.chrono.chrono.services.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.*;
import com.chrono.chrono.repositories.crm.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional
    public CustomerContact addContact(Customer customer, CustomerContact contact) {
        contact.setCustomer(customer);
        return customerContactRepository.save(contact);
    }

    @Transactional
    public CrmActivity logActivity(Customer customer, CustomerContact contact, CrmActivity activity) {
        activity.setCustomer(customer);
        activity.setContact(contact);
        return crmActivityRepository.save(activity);
    }

    @Transactional
    public CrmLead saveLead(CrmLead lead) {
        return crmLeadRepository.save(lead);
    }

    @Transactional
    public Opportunity saveOpportunity(Opportunity opportunity) {
        return opportunityRepository.save(opportunity);
    }

    @Transactional
    public Campaign saveCampaign(Campaign campaign) {
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
