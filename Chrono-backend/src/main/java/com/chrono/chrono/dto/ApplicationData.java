package com.chrono.chrono.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class ApplicationData {
    private String companyName;
    private String contactName;
    private String email;
    private String phone;
    private String additionalInfo;

    // Neu: Features-Baukasten
    private List<String> selectedFeatures;        // z.B. ["base", "payroll", "chatbot"]
    private List<String> selectedFeatureNames;    // z.B. ["Zeiterfassung (Basis)", "Lohnabrechnung", ...]
    private String featureSummary;                // z.B. "Zeiterfassung (Basis), Lohnabrechnung, Chatbot"

    private Integer employeeCount;    // z. B. 42
    private String billingPeriod;     // "monthly" / "yearly"
    private Double calculatedPrice;   // z. B. 99.00

    // Optionales Intensiv-Onboarding
    private Boolean includeOptionalTraining;
    private Double optionalTrainingCost;  // 120.00 CHF, wenn ausgewählt
}
