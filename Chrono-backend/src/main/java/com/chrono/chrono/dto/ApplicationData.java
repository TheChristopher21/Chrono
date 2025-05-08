package com.chrono.chrono.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ApplicationData {
    private String companyName;
    private String contactName;
    private String email;
    private String phone;
    private String additionalInfo;

    private String chosenPackage;
    private Integer employeeCount;    // z. B. 42
    private String billingPeriod;     // "monthly" / "yearly"
    private Double calculatedPrice;   // z. B. 99.00


}