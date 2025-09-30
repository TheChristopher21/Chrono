package com.chrono.chrono.entities.crm;

import com.chrono.chrono.entities.Customer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "crm_activities")
public class CrmActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    @JsonIgnore
    private CustomerContact contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrmActivityType type = CrmActivityType.NOTE;

    @Column(length = 2048)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    private String owner;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public CustomerContact getContact() {
        return contact;
    }

    public void setContact(CustomerContact contact) {
        this.contact = contact;
    }

    public CrmActivityType getType() {
        return type;
    }

    public void setType(CrmActivityType type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
