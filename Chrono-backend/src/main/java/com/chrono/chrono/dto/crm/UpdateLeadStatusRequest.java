package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.LeadStatus;

public class UpdateLeadStatusRequest {
    private LeadStatus status;

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }
}
