package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.ServiceRequestStatus;

import java.time.LocalDate;

public class UpdateServiceRequestStatusRequest {
    private ServiceRequestStatus status;
    private LocalDate closedDate;

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceRequestStatus status) {
        this.status = status;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }
}
