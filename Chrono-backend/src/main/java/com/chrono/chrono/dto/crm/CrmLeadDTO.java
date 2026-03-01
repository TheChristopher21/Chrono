package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmLead;
import com.chrono.chrono.entities.crm.LeadStatus;

import java.time.LocalDate;

public class CrmLeadDTO {
    private final Long id;
    private final String companyName;
    private final String contactName;
    private final String email;
    private final String phone;
    private final String source;
    private final LeadStatus status;
    private final LocalDate createdDate;

    public CrmLeadDTO(Long id, String companyName, String contactName, String email, String phone,
                      String source, LeadStatus status, LocalDate createdDate) {
        this.id = id;
        this.companyName = companyName;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.source = source;
        this.status = status;
        this.createdDate = createdDate;
    }

    public static CrmLeadDTO from(CrmLead lead) {
        return new CrmLeadDTO(
                lead.getId(),
                lead.getCompanyName(),
                lead.getContactName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getStatus(),
                lead.getCreatedDate());
    }

    public Long getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSource() {
        return source;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }
}
