package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Company;

public class CompanySettingsDTO {
    private Double uvgBuRate;
    private Double uvgNbuRate;
    private Double ktgRateEmployee;
    private Double ktgRateEmployer;
    private Double fakRate;
    private Double midijobFactor;

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

    public static CompanySettingsDTO fromEntity(Company c) {
        CompanySettingsDTO dto = new CompanySettingsDTO();
        dto.setUvgBuRate(c.getUvgBuRate());
        dto.setUvgNbuRate(c.getUvgNbuRate());
        dto.setKtgRateEmployee(c.getKtgRateEmployee());
        dto.setKtgRateEmployer(c.getKtgRateEmployer());
        dto.setFakRate(c.getFakRate());
        dto.setMidijobFactor(c.getMidijobFactor());
        return dto;
    }

    public void applyToEntity(Company c) {
        c.setUvgBuRate(this.uvgBuRate);
        c.setUvgNbuRate(this.uvgNbuRate);
        c.setKtgRateEmployee(this.ktgRateEmployee);
        c.setKtgRateEmployer(this.ktgRateEmployer);
        c.setFakRate(this.fakRate);
        c.setMidijobFactor(this.midijobFactor);
    }
}
