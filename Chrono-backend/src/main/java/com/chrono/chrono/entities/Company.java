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

    // Webhook URLs für externe Benachrichtigungen
    private String slackWebhookUrl;
    private String teamsWebhookUrl;

    // Einstellungen, welche Benachrichtigungen gesendet werden sollen
    private Boolean notifyVacation;
    private Boolean notifyOvertime;

    // Feature-Flag: Kundenbasierte Zeiterfassung
    @Column(name = "customer_tracking_enabled")
    private Boolean customerTrackingEnabled;

    // NEU: Kantonskürzel für Feiertagsberechnung (z.B. "SG", "ZH")
    @Column(name = "canton_abbreviation", length = 2)
    private String cantonAbbreviation;


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

    public String getCantonAbbreviation() { return cantonAbbreviation; } // NEUER GETTER
    public void setCantonAbbreviation(String cantonAbbreviation) { this.cantonAbbreviation = cantonAbbreviation; } // NEUER SETTER

    public Set<User> getUsers() { return users; }
    public void setUsers(Set<User> users) { this.users = users; }

    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public void setSlackWebhookUrl(String slackWebhookUrl) { this.slackWebhookUrl = slackWebhookUrl; }

    public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
    public void setTeamsWebhookUrl(String teamsWebhookUrl) { this.teamsWebhookUrl = teamsWebhookUrl; }

    public Boolean getNotifyVacation() { return notifyVacation; }
    public void setNotifyVacation(Boolean notifyVacation) { this.notifyVacation = notifyVacation; }

    public Boolean getNotifyOvertime() { return notifyOvertime; }
    public void setNotifyOvertime(Boolean notifyOvertime) { this.notifyOvertime = notifyOvertime; }

    public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; }
    public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; }
}