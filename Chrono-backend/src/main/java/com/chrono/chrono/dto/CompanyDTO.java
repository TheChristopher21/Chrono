package com.chrono.chrono.dto;

public class CompanyDTO {

    private Long   id;
    private String name;
    private boolean active;
    private int    userCount;   // Anzahl der User in dieser Firma
    private boolean paid;
    private String  paymentMethod;
    private boolean canceled;

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
    }

    /* ---------- Getter / Setter ---------- */
    public Long getId()               { return id; }
    public void setId(Long id)        { this.id = id; }

    public String getName()           { return name; }
    public void setName(String name)  { this.name = name; }

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

    public static CompanyDTO fromEntity(com.chrono.chrono.entities.Company co) {
        return new CompanyDTO(
                co.getId(),
                co.getName(),
                co.isActive(),
                co.getUsers() != null ? co.getUsers().size() : 0,
                co.isPaid(),
                co.getPaymentMethod(),
                co.isCanceled()
        );
    }
}
