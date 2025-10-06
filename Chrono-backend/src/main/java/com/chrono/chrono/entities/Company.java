package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    // Firmensitz für Rechnungen
    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String city;

    private boolean active = true;

    // Zahlungsinformationen
    private boolean paid = false;
    private String  paymentMethod;
    private boolean canceled = false;

    // Webhook URLs für externe Benachrichtigungen
    private String slackWebhookUrl;
    private String teamsWebhookUrl;

    // Pfad zum Firmenlogo
    private String logoPath;

    // Einstellungen, welche Benachrichtigungen gesendet werden sollen
    private Boolean notifyVacation;
    private Boolean notifyOvertime;

    @Column(name = "customer_tracking_enabled")
    private Boolean customerTrackingEnabled;

    @Column(name = "demo_data_last_reset")
    private LocalDate demoDataLastReset;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_enabled_features", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "feature_key")
    private Set<String> enabledFeatures = new LinkedHashSet<>();

    // NEU: Kantonskürzel für Feiertagsberechnung (z.B. "SG", "ZH")
    @Column(name = "canton_abbreviation", length = 2)
    private String cantonAbbreviation;

    // Sozialversicherungsraten (konfigurierbar je Firma)
    private Double uvgBuRate;       // Unfallversicherung Betrieb (AG)
    private Double uvgNbuRate;      // Nichtberufsunfall (AN)
    private Double ktgRateEmployee; // Krankentaggeld AN-Anteil
    private Double ktgRateEmployer; // Krankentaggeld AG-Anteil
    private Double fakRate;         // Familienausgleichskasse
    private Double midijobFactor;   // Faktor für Midijob-Reduktion in DE


    // Beispiel: Jeder Company kann beliebig viele Users haben
    @OneToMany(mappedBy = "company")
    @JsonManagedReference("company-users")
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

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

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

    public Set<String> getEnabledFeatures() {
        if (enabledFeatures == null) {
            enabledFeatures = new LinkedHashSet<>();
        }
        return enabledFeatures;
    }

    public void setEnabledFeatures(Set<String> enabledFeatures) {
        this.enabledFeatures = (enabledFeatures != null)
                ? new LinkedHashSet<>(enabledFeatures)
                : new LinkedHashSet<>();
    }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public Double getUvgBuRate() { return uvgBuRate; }
    public void setUvgBuRate(Double uvgBuRate) { this.uvgBuRate = uvgBuRate; }

    public Double getUvgNbuRate() { return uvgNbuRate; }
    public void setUvgNbuRate(Double uvgNbuRate) { this.uvgNbuRate = uvgNbuRate; }

    public Double getKtgRateEmployee() { return ktgRateEmployee; }
    public void setKtgRateEmployee(Double ktgRateEmployee) { this.ktgRateEmployee = ktgRateEmployee; }

    public Double getKtgRateEmployer() { return ktgRateEmployer; }
    public void setKtgRateEmployer(Double ktgRateEmployer) { this.ktgRateEmployer = ktgRateEmployer; }

    public Double getFakRate() { return fakRate; }
    public void setFakRate(Double fakRate) { this.fakRate = fakRate; }

    public Double getMidijobFactor() { return midijobFactor; }
    public void setMidijobFactor(Double midijobFactor) { this.midijobFactor = midijobFactor; }

    public LocalDate getDemoDataLastReset() { return demoDataLastReset; }
    public void setDemoDataLastReset(LocalDate demoDataLastReset) { this.demoDataLastReset = demoDataLastReset; }
}