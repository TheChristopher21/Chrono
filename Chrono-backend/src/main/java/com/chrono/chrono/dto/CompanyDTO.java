package com.chrono.chrono.dto;

public class CompanyDTO {

    private Long   id;
    private String name;
    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String city;
    private boolean active;
    private int    userCount;   // Anzahl der User in dieser Firma
    private boolean paid;
    private String  paymentMethod;
    private boolean canceled;
    private Boolean customerTrackingEnabled;

    public CompanyDTO() {}

    public CompanyDTO(Long id, String name, boolean active, int userCount,
                      boolean paid, String paymentMethod, boolean canceled) {
        this.id            = id;
        this.name          = name;
        this.active        = active;
        this.userCount     = userCount;
        this.paid          = paid;
        this.paymentMethod = paymentMethod;
        this.canceled      = canceled;
        this.customerTrackingEnabled = null;
    }

    /* ---------- Getter / Setter ---------- */
    public Long getId()               { return id; }
    public void setId(Long id)        { this.id = id; }

    public String getName()           { return name; }
    public void setName(String name)  { this.name = name; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public boolean isActive()         { return active; }
    public void setActive(boolean a)  { this.active = a; }

    public int getUserCount()         { return userCount; }
    public void setUserCount(int uc)  { this.userCount = uc; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; }
    public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; }

    public static CompanyDTO fromEntity(com.chrono.chrono.entities.Company co) {
        CompanyDTO dto = new CompanyDTO(
                co.getId(),
                co.getName(),
                co.isActive(),
                co.getUsers() != null ? co.getUsers().size() : 0,
                co.isPaid(),
                co.getPaymentMethod(),
                co.isCanceled()
        );
        dto.setCustomerTrackingEnabled(co.getCustomerTrackingEnabled());
        dto.setAddressLine1(co.getAddressLine1());
        dto.setAddressLine2(co.getAddressLine2());
        dto.setPostalCode(co.getPostalCode());
        dto.setCity(co.getCity());
        return dto;
    }
}
