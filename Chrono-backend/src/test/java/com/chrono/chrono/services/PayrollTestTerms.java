package com.chrono.chrono.services;

import com.chrono.chrono.services.payroll.PayrollTerms;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class PayrollTestTerms {
    private PayrollTestTerms() {}
    public static PayrollTerms swiss() {
        return new PayrollTerms("CH", "SALARY", new BigDecimal("5000"), new BigDecimal("40"),
                new BigDecimal("30"), new BigDecimal("0.25"), 45, "CH_ORDINARY", null, null, true,
                LocalDate.of(1990, 1, 1), LocalDate.of(2025, 1, 1),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
    public static PayrollTerms german() {
        return change(swiss(), Map.ofEntries(Map.entry("country", "DE"), Map.entry("taxMode", "DE_STANDARD"),
                Map.entry("taxClass", 1), Map.entry("childAllowance", 0), Map.entry("churchRate", 0),
                Map.entry("healthAdditionalRate", "0.029"), Map.entry("parent", false), Map.entry("childrenUnder25", 0),
                Map.entry("saxony", false), Map.entry("taxAllowance", 0), Map.entry("taxAddition", 0), Map.entry("taxFactor", 1),
                Map.entry("u1Rate", "0.01"), Map.entry("u2Rate", "0.002"), Map.entry("insolvencyRate", "0.0015")));
    }
    public static PayrollTerms change(PayrollTerms original, Map<String, ?> changes) {
        var json = new ObjectMapper().findAndRegisterModules();
        var tree = json.valueToTree(original);
        changes.forEach((key, value) -> ((com.fasterxml.jackson.databind.node.ObjectNode) tree).set(key, json.valueToTree(value)));
        return json.convertValue(tree, PayrollTerms.class);
    }
}
