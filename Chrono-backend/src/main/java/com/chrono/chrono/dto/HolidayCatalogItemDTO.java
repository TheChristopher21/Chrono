package com.chrono.chrono.dto;

public class HolidayCatalogItemDTO {
    private String code;
    private String name;
    private String country;
    private String regionHint;

    public HolidayCatalogItemDTO() {}

    public HolidayCatalogItemDTO(String code, String name, String country, String regionHint) {
        this.code = code;
        this.name = name;
        this.country = country;
        this.regionHint = regionHint;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegionHint() { return regionHint; }
    public void setRegionHint(String regionHint) { this.regionHint = regionHint; }
}
