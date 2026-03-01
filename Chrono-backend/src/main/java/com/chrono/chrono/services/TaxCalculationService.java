package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TaxCalculationService {

    private static final double DE_RV_ALV_THRESHOLD = 8050.0;   // Beitragsbemessungsgrenze RV/ALV (monatlich)
    private static final double DE_KV_PV_THRESHOLD = 5512.5;    // Beitragsbemessungsgrenze KV/PV (monatlich)
    private static final double DE_KV_ADDITIONAL_DEFAULT = 0.0125; // durchschnittlicher Zusatzbeitrag KV (AN-Hälfte)

    private static final double CH_ALV_THRESHOLD = 148200.0 / 12.0; // monatliche Grenze ALV (Jahresgrenze 148'200)
    private static final double CH_BVG_COORDINATION = 26460.0 / 12.0; // monatlicher Koordinationsabzug
    private static final double CH_BVG_MAX = 64260.0 / 12.0; // maximal versicherter Monatslohn
    private static final double CH_BVG_ENTRY = 22680.0 / 12.0; // Eintrittsschwelle für BVG-Pflicht

    private static final double DE_MINIJOB_LIMIT = 556.0;  // keine Beiträge unterhalb
    private static final double DE_MIDIJOB_LIMIT = 2000.0; // reduzierte Beiträge bis hierhin

    public static class Result {
        private final Map<String, Double> employee;
        private final Map<String, Double> employer;

        public Result(Map<String, Double> employee, Map<String, Double> employer) {
            this.employee = employee;
            this.employer = employer;
        }

        public Map<String, Double> getEmployee() { return employee; }
        public Map<String, Double> getEmployer() { return employer; }
        public double getEmployeeTotal() {
            return employee.values().stream().mapToDouble(Double::doubleValue).sum();
        }
        public double getEmployerTotal() {
            return employer.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    public Result calculate(User user, double gross) {
        String c = user.getCountry() != null ? user.getCountry().trim().toUpperCase() : "";
        return switch (c) {
            case "DE", "GER", "GERMANY" -> calculateGermany(user, gross);
            case "CH", "CHE", "SWITZERLAND" -> calculateSwitzerland(user, gross);
            default -> calculateSimple(gross);
        };
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Result calculateSimple(double gross) {
        Map<String, Double> emp = new LinkedHashMap<>();
        emp.put("Tax", round2(gross * 0.15));
        emp.put("Social", round2(gross * 0.05));
        return new Result(emp, new LinkedHashMap<>());
    }

    private double determineWeeklyHours(User user) {
        Double daily = user.getDailyWorkHours();
        Integer days = user.getExpectedWorkDays();
        if (daily != null && days != null) {
            return daily * days;
        }
        return 0;
    }

    private Result calculateGermany(User user, double gross) {
        if (gross <= DE_MINIJOB_LIMIT) {
            // Minijob: keine regulären Abzüge
            return new Result(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        double incomeTax;
        if (gross <= 10908) {
            incomeTax = 0;
        } else if (gross <= 15999) {
            incomeTax = (gross - 10908) * 0.14;
        } else if (gross <= 62840) {
            incomeTax = 705 + (gross - 15999) * 0.24;
        } else if (gross <= 277825) {
            incomeTax = 13761 + (gross - 62840) * 0.42;
        } else {
            incomeTax = 102047 + (gross - 277825) * 0.45;
        }

        double soli = incomeTax > 18130.0 / 12 ? incomeTax * 0.055 : 0;
        boolean churchMember = Boolean.TRUE.equals(user.getChurchTax());
        double churchRate = 0.09;
        if (churchMember && user.getFederalState() != null) {
            String s = user.getFederalState().trim().toUpperCase();
            if (s.equals("BY") || s.contains("BAYERN") || s.contains("BAVARIA") ||
                    s.equals("BW") || s.contains("BADEN-WUERTTEMBERG") || s.contains("BADEN-WÜRTTEMBERG")) {
                churchRate = 0.08;
            }
        }
        double churchTax = churchMember ? incomeTax * churchRate : 0;

        double rvBase = Math.min(gross, DE_RV_ALV_THRESHOLD);
        double rv = rvBase * 0.093; // pension insurance employee share

        double kvBase = Math.min(gross, DE_KV_PV_THRESHOLD);
        double addRate = user.getGkvAdditionalRate() != null ? user.getGkvAdditionalRate() : DE_KV_ADDITIONAL_DEFAULT;
        double kv = kvBase * (0.073 + addRate); // health insurance including Zusatzbeitrag

        int age = user.getBirthDate() != null ? Period.between(user.getBirthDate(), LocalDate.now()).getYears() : 0;
        double pvRate;
        Integer kids = user.getChildren();
        if (kids == null) {
            kids = 0;
        }
        if (age >= 23 && kids == 0) {
            pvRate = 0.024; // childless surcharge
        } else if (kids >= 4) {
            pvRate = 0.0155;
        } else if (kids == 3) {
            pvRate = 0.0165;
        } else if (kids == 2) {
            pvRate = 0.017;
        } else {
            pvRate = 0.018;
        }
        double pv = kvBase * pvRate; // same threshold as KV

        double alv = rvBase * 0.013; // unemployment insurance employee share

        double reduction = 1.0;
        if (gross <= DE_MIDIJOB_LIMIT) {
            Double factor = user.getCompany() != null ? user.getCompany().getMidijobFactor() : null;
            reduction = factor != null ? factor : 0.5;
        }
        rv *= reduction;
        kv *= reduction;
        pv *= reduction;
        alv *= reduction;

        Map<String, Double> emp = new LinkedHashMap<>();
        emp.put("Income tax", round2(incomeTax));
        if (soli > 0) emp.put("Solidarity surcharge", round2(soli));
        if (churchTax > 0) emp.put("Church tax", round2(churchTax));
        emp.put("Pension insurance", round2(rv));
        emp.put("Health insurance", round2(kv));
        emp.put("Nursing insurance", round2(pv));
        emp.put("Unemployment insurance", round2(alv));

        double employerRv = rvBase * 0.093;
        double employerKv = kvBase * (0.073 + addRate);
        double employerPv = kvBase * 0.017;
        double employerAlv = rvBase * 0.013;
        double employerMisc = rvBase * 0.0015; // Insolvenzgeldumlage

        Map<String, Double> empEr = new LinkedHashMap<>();
        empEr.put("Pension insurance", round2(employerRv));
        empEr.put("Health insurance", round2(employerKv));
        empEr.put("Nursing insurance", round2(employerPv));
        empEr.put("Unemployment insurance", round2(employerAlv));
        empEr.put("Insolvency levy", round2(employerMisc));

        return new Result(emp, empEr);
    }

    private Result calculateSwitzerland(User user, double gross) {
        double incomeTax;
        if (gross <= 14500) {
            incomeTax = 0;
        } else if (gross <= 58700) {
            incomeTax = (gross - 14500) * 0.08;
        } else if (gross <= 215000) {
            incomeTax = 3556 + (gross - 58700) * 0.11;
        } else {
            incomeTax = 21117 + (gross - 215000) * 0.13;
        }

        double weeklyHours = determineWeeklyHours(user);

        double ahvEmployee = gross * 0.053;
        double ahvEmployer = gross * 0.053;

        double alvBase = Math.min(gross, CH_ALV_THRESHOLD);
        double alvEmployee = alvBase * 0.011;
        double alvEmployer = alvBase * 0.011;

        int age = user.getBirthDate() != null ? Period.between(user.getBirthDate(), LocalDate.now()).getYears() : 0;
        double bvgRate;
        if (age < 25) {
            bvgRate = 0.0;
        } else if (age <= 34) {
            bvgRate = 0.07;
        } else if (age <= 44) {
            bvgRate = 0.10;
        } else if (age <= 54) {
            bvgRate = 0.15;
        } else {
            bvgRate = 0.18;
        }
        double insured = 0;
        if (gross >= CH_BVG_ENTRY) {
            insured = Math.max(0, Math.min(gross - CH_BVG_COORDINATION, CH_BVG_MAX));
        }
        double bvgEmployee = insured * bvgRate;
        double bvgEmployer = insured * bvgRate;

        double uvgNbuRate = user.getCompany() != null && user.getCompany().getUvgNbuRate() != null
                ? user.getCompany().getUvgNbuRate() : 0.012;
        double uvgBuRate = user.getCompany() != null && user.getCompany().getUvgBuRate() != null
                ? user.getCompany().getUvgBuRate() : 0.012;
        double uvgEmployee = weeklyHours >= 8 ? gross * uvgNbuRate : 0;
        double uvgEmployer = gross * uvgBuRate;

        double ktgEmpRate = user.getCompany() != null && user.getCompany().getKtgRateEmployee() != null
                ? user.getCompany().getKtgRateEmployee() : 0.005;
        double ktgErRate = user.getCompany() != null && user.getCompany().getKtgRateEmployer() != null
                ? user.getCompany().getKtgRateEmployer() : 0.0;
        double taggeldEmployee = gross * ktgEmpRate;
        double taggeldEmployer = gross * ktgErRate;

        double fakRate = user.getCompany() != null && user.getCompany().getFakRate() != null
                ? user.getCompany().getFakRate() : 0.02;
        double fak = gross * fakRate;  // employer only

        boolean qst = user.getTarifCode() != null && user.getTarifCode().toUpperCase().startsWith("Q");
        double withholding = qst ? gross * 0.10 : 0.0; // simplified Quellensteuer

        Map<String, Double> emp = new LinkedHashMap<>();
        emp.put("Income tax", round2(incomeTax));
        if (withholding > 0) emp.put("Withholding tax", round2(withholding));
        emp.put("AHV/IV/EO", round2(ahvEmployee));
        emp.put("ALV", round2(alvEmployee));
        if (bvgEmployee > 0) emp.put("BVG", round2(bvgEmployee));
        if (uvgEmployee > 0) emp.put("NBU", round2(uvgEmployee));
        if (taggeldEmployee > 0) emp.put("KTG", round2(taggeldEmployee));

        Map<String, Double> empEr = new LinkedHashMap<>();
        empEr.put("AHV/IV/EO", round2(ahvEmployer));
        empEr.put("ALV", round2(alvEmployer));
        if (bvgEmployer > 0) empEr.put("BVG", round2(bvgEmployer));
        empEr.put("BU", round2(uvgEmployer));
        if (taggeldEmployer > 0) empEr.put("KTG", round2(taggeldEmployer));
        empEr.put("FAK", round2(fak));

        return new Result(emp, empEr);
    }
}
