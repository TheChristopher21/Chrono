package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.ServiceRequest;
import com.chrono.chrono.entities.inventory.ServiceRequestStatus;

import java.time.LocalDate;

public class ServiceRequestDTO {
    private final Long id;
    private final String customerName;
    private final String subject;
    private final String description;
    private final ServiceRequestStatus status;
    private final LocalDate openedDate;
    private final LocalDate closedDate;

    public ServiceRequestDTO(Long id,
                             String customerName,
                             String subject,
                             String description,
                             ServiceRequestStatus status,
                             LocalDate openedDate,
                             LocalDate closedDate) {
        this.id = id;
        this.customerName = customerName;
        this.subject = subject;
        this.description = description;
        this.status = status;
        this.openedDate = openedDate;
        this.closedDate = closedDate;
    }

    public static ServiceRequestDTO from(ServiceRequest request) {
        return new ServiceRequestDTO(
                request.getId(),
                request.getCustomerName(),
                request.getSubject(),
                request.getDescription(),
                request.getStatus(),
                request.getOpenedDate(),
                request.getClosedDate()
        );
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }
}
