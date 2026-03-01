package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmLead;
import com.chrono.chrono.entities.crm.LeadStatus;

public class CreateCrmLeadRequest {
    private String companyName;
    private String contactName;
    private String email;
    private String phone;
    private String source;
    private LeadStatus status = LeadStatus.NEW;

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }

    public CrmLead toEntity() {
        CrmLead lead = new CrmLead();
        lead.setCompanyName(companyName);
        lead.setContactName(contactName);
        lead.setEmail(email);
        lead.setPhone(phone);
        lead.setSource(source);
        lead.setStatus(status);
        return lead;
    }
}
