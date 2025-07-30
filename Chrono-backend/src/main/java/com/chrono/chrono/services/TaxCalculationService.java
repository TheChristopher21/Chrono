package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
public class TaxCalculationService {

    private static final double DE_RV_ALV_THRESHOLD = 8050.0;   // Beitragsbemessungsgrenze RV/ALV (monatlich)
    private static final double DE_KV_PV_THRESHOLD = 5512.5;    // Beitragsbemessungsgrenze KV/PV (monatlich)
    private static final double DE_KV_ADDITIONAL = 0.0125;      // durchschnittlicher Zusatzbeitrag KV (AN-Anteil)

    private static final double CH_ALV_THRESHOLD = 148200.0 / 12.0; // monatliche Grenze ALV
    private static final double CH_BVG_COORDINATION = 26460.0 / 12.0; // monatlicher Koordinationsabzug
    private static final double CH_BVG_MAX = 64260.0 / 12.0; // maximal versicherter Monatslohn
    private static final double CH_BVG_ENTRY = 22680.0 / 12.0; // Eintrittsschwelle für BVG-Pflicht

    private static final double DE_MINIJOB_LIMIT = 556.0;  // keine Beiträge unterhalb
    private static final double DE_MIDIJOB_LIMIT = 2000.0; // reduzierte Beiträge bis hierhin

    public static class Result {
        private final double tax;
        private final double social;
        private final double employer;

        public Result(double tax, double social, double employer) {
            this.tax = tax;
            this.social = social;
            this.employer = employer;
        }

        public double getTax() { return tax; }
        public double getSocial() { return social; }
        public double getEmployer() { return employer; }
        public double getTotal() { return tax + social; }
    }

    public Result calculate(User user, double gross) {
        String c = user.getCountry() != null ? user.getCountry().trim().toUpperCase() : "";
        return switch (c) {
            case "DE", "GER", "GERMANY" -> calculateGermany(user, gross);
            case "CH", "CHE", "SWITZERLAND" -> calculateSwitzerland(user, gross);
            default -> calculateSimple(gross);
        };
    }

    private Result calculateSimple(double gross) {
        double tax = gross * 0.15;
        double social = gross * 0.05;
        return new Result(tax, social, 0);
    }

    private Result calculateGermany(User user, double gross) {
        if (gross <= DE_MINIJOB_LIMIT) {
            // Minijob: keine regulären Abzüge
            return new Result(0, 0, 0);
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
        boolean churchMember = user.getReligion() != null && !user.getReligion().isBlank();
        double churchTax = churchMember ? incomeTax * 0.09 : 0;
        double tax = incomeTax + soli + churchTax;

        double rvBase = Math.min(gross, DE_RV_ALV_THRESHOLD);
        double rv = rvBase * 0.093; // pension insurance employee share

        double kvBase = Math.min(gross, DE_KV_PV_THRESHOLD);
        double kv = kvBase * (0.073 + DE_KV_ADDITIONAL); // health insurance including Zusatzbeitrag

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

        double reduction = gross <= DE_MIDIJOB_LIMIT ? 0.5 : 1.0;
        rv *= reduction;
        kv *= reduction;
        pv *= reduction;
        alv *= reduction;

        double social = rv + kv + pv + alv;

        double employerMisc = rvBase * 0.0015; // Insolvenzgeldumlage ca. 0,15 %
        double employerNormal = rvBase * 0.093 + kvBase * (0.073 + DE_KV_ADDITIONAL) + kvBase * 0.018 + rvBase * 0.013;
        double employer = employerNormal + employerMisc;

        return new Result(tax, social, employer);
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

        double ahv = gross * 0.053; // AHV/IV/EO employee share

        double alvBase = Math.min(gross, CH_ALV_THRESHOLD);
        double alv = alvBase * 0.011;

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
        double bvg = insured * bvgRate;

        double uvg = gross * 0.012; // accident insurance (average)

        double fak = gross * 0.02;  // family allowance fund (employer)
        double taggeld = gross * 0.005; // sickness daily allowance

        double social = ahv + alv + bvg + uvg + taggeld;
        double employer = social + fak; // employer pays FAK entirely

        boolean qst = user.getTarifCode() != null && user.getTarifCode().toUpperCase().startsWith("Q");
        double withholding = qst ? gross * 0.10 : 0.0; // simplified Quellensteuer

        return new Result(incomeTax + withholding, social, employer);
    }
}
