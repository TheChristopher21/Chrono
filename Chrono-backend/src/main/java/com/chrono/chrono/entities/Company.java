package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Neues Entity: Repräsentiert eine Firma / Mandant.
 */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean active = true;

    // Zahlungsinformationen
    private boolean paid = false;
    private String  paymentMethod;
    private boolean canceled = false;


    // Beispiel: Jeder Company kann beliebig viele Users haben
    @OneToMany(mappedBy = "company")
    @JsonManagedReference
    private Set<User> users = new HashSet<>();

    public Company() {}

    public Company(String name) {
        this.name = name;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }


    public Set<User> getUsers() { return users; }
    public void setUsers(Set<User> users) { this.users = users; }
}
