package com.chrono.chrono.services.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrmServiceTest {

    @Autowired
    private CrmService crmService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void manageCrmArtifactsAndQueriesByStatus() {
        Company company = companyRepository.save(new Company("Chrono CRM"));

        Customer customer = new Customer();
        customer.setName("ACME AG");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        CustomerAddress address = new CustomerAddress();
        address.setType(AddressType.BILLING);
        address.setStreet("Market Street 1");
        address.setPostalCode("8000");
        address.setCity("Zürich");
        address.setCountry("CH");
        crmService.addAddress(customer, address);

        CustomerContact contact = new CustomerContact();
        contact.setFirstName("Anna");
        contact.setLastName("Meier");
        contact.setEmail("anna@acme.ch");
        contact.setPhone("+41 44 123 45 67");
        crmService.addContact(customer, contact);

        CrmActivity activity = new CrmActivity();
        activity.setType(CrmActivityType.CALL);
        activity.setNotes("Kick-off call");
        activity.setOwner("sales@chrono");
        crmService.logActivity(customer, contact, activity);

        CrmLead lead = new CrmLead();
        lead.setCompany(company);
        lead.setCompanyName("ACME AG");
        lead.setContactName("Anna Meier");
        lead.setEmail("anna@acme.ch");
        lead.setStatus(LeadStatus.QUALIFIED);
        crmService.saveLead(lead);

        Opportunity opportunity = new Opportunity();
        opportunity.setCompany(company);
        opportunity.setCustomer(customer);
        opportunity.setTitle("ERP Implementation");
        opportunity.setStage(OpportunityStage.PROPOSAL);
        opportunity.setValue(new BigDecimal("120000"));
        opportunity.setExpectedCloseDate(LocalDate.now().plusMonths(2));
        crmService.saveOpportunity(opportunity);

        Campaign campaign = new Campaign();
        campaign.setCompany(company);
        campaign.setName("Swiss Launch");
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setChannel("Email");
        campaign.setBudget(15000);
        crmService.saveCampaign(campaign);

        CrmDocument document = new CrmDocument();
        document.setFileName("offer.pdf");
        document.setUrl("https://example.com/offer.pdf");
        document.setUploadedBy("sales@chrono");
        crmService.saveDocument(customer, document);

        List<CustomerContact> contacts = crmService.listContacts(customer);
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getEmail()).isEqualTo("anna@acme.ch");

        List<CrmActivity> activities = crmService.listActivities(customer);
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getNotes()).contains("Kick-off");

        List<CrmDocument> documents = crmService.listDocuments(customer);
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getFileName()).isEqualTo("offer.pdf");

        List<CrmLead> qualifiedLeads = crmService.getLeadsByStatus(company, LeadStatus.QUALIFIED);
        assertThat(qualifiedLeads).extracting(CrmLead::getCompanyName).contains("ACME AG");

        List<Opportunity> proposalStage = crmService.getOpportunitiesByStage(company, OpportunityStage.PROPOSAL);
        assertThat(proposalStage).hasSize(1);
        assertThat(proposalStage.get(0).getValue()).isEqualByComparingTo(new BigDecimal("120000"));

        List<Campaign> runningCampaigns = crmService.getCampaignsByStatus(company, CampaignStatus.ACTIVE);
        assertThat(runningCampaigns).extracting(Campaign::getName).contains("Swiss Launch");
    }
}
