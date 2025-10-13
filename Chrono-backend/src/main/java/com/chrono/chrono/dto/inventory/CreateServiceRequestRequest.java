package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.ServiceRequest;
import com.chrono.chrono.entities.inventory.ServiceRequestStatus;

import java.time.LocalDate;

public class CreateServiceRequestRequest {
    private String customerName;
    private String subject;
    private String description;
    private ServiceRequestStatus status;
    private LocalDate openedDate;
    private LocalDate closedDate;

    public ServiceRequest toEntity() {
        ServiceRequest request = new ServiceRequest();
        request.setCustomerName(customerName);
        request.setSubject(subject);
        request.setDescription(description);
        if (status != null) {
            request.setStatus(status);
        }
        if (openedDate != null) {
            request.setOpenedDate(openedDate);
        }
        if (closedDate != null) {
            request.setClosedDate(closedDate);
        }
        return request;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceRequestStatus status) {
        this.status = status;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(LocalDate openedDate) {
        this.openedDate = openedDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }
}
