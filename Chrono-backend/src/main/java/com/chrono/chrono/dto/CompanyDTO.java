package com.chrono.chrono.dto;

public class CompanyDTO {

    private Long   id;
    private String name;
    private boolean active;
    private int    userCount;   // Anzahl der User in dieser Firma

    public CompanyDTO() {}

    public CompanyDTO(Long id, String name, boolean active, int userCount) {
        this.id        = id;
        this.name      = name;
        this.active    = active;
        this.userCount = userCount;
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
}
