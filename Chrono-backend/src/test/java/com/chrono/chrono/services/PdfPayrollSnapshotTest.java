package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PdfPayrollSnapshotTest {
    @Test void historicalSettlementPdfSeparatesUnchangedOriginalsFromNewBasesAndSignedCharges() throws Exception {
        for(String currency:List.of("CHF","EUR")){
            Payslip slip=fixture(currency);boolean de=currency.equals("EUR");slip.setGrossSalary(500.0);slip.setDeductions(600.0);slip.setNetSalary(-100.0);slip.setEarnings(List.of(new PayComponent("Arrears payment",500.0)));
            slip.setDeductionsList(List.of(new PayComponent((de?"Pension insurance":"Withholding tax")+" · Altperiode 2025-12-01",600.0)));
            var original=Map.of("periodStart","2025-12-01","gross",Map.of("total",5000),"germanBasis",Map.of("pensionBase",5000,"healthBase",5000));
            var revised=Map.of("periodStart","2025-12-01","remuneration",Map.of("taxGross",Map.of("total",5500),"socialGross",Map.of("total",5500)),"germanBasis",Map.of("pensionBase",5500,"healthBase",5500));
            slip.setCalculationSnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of("terms",de?PayrollTestTerms.german():PayrollTestTerms.swiss(),"remuneration",Map.of("assessment",Map.of("originalPeriods",List.of(original),"revisedPeriods",List.of(revised),"employee",Map.of("Pension insurance · Altperiode 2025-12-01",600))))));
            String content=render(slip,"v8-historical-"+currency+".pdf");for(String expected:List.of("Differenzausgleich für Altperioden","Originalwerte bleiben unverändert","2025-12-01","5000 "+currency,"5500 "+currency,"Rückforderung / Verrechnung","keine Bankzahlung",de?"Rentenversicherung":"Quellensteuer"))assertTrue(content.contains(expected),expected);
        }
    }
    @Test void supplementaryPaymentPdfShowsSeparateBasesAndActualInsuranceSegments() throws Exception {
        Payslip slip=fixture("CHF");
        var payment=Map.of("reference","CH-2026-Nachzahlung-01","gross",500,"earnedFrom","2026-01-01","earnedThrough","2026-01-31","receivedOn","2026-03-15","claimEstablishedOn","2026-01-31");
        slip.setCalculationSnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of(
                "terms",PayrollTestTerms.swiss(),"gross",Map.of("total",5500),
                "remuneration",Map.of("taxGross",Map.of("total",5500),"socialGross",Map.of("total",5000),"applied",List.of(Map.of("payment",payment))),
                "swissBasis",Map.of("insuranceSlices",List.of(
                        Map.of("from","2026-03-01","through","2026-03-15","uvgBase",2500,"ktgBase",2500,"pensionEmployee",100,"pensionEmployer",150),
                        Map.of("from","2026-03-16","through","2026-03-31","uvgBase",2500,"ktgBase",2500,"pensionEmployee",110,"pensionEmployer",160))))));
        String text=render(slip,"v7-swiss-payment-insurance.pdf");
        for(String expected:List.of("Zuordnung der Nachzahlungen","Bruttozufluss dieser Periode","Steuerlich zugeordnetes Brutto","Der SV-Periode zugeordnetes Brutto","CH-2026-Nachzahlung-01","2026-03-15","2026-01-31","Versicherung nach Deckungsabschnitt","100 / 150","110 / 160"))assertTrue(text.contains(expected),expected);
    }
    @Test void marchClausePdfShowsDocumentedPriorYearBasesInsteadOfAnother2026Deduction() throws Exception {
        Payslip slip=fixture("EUR");
        slip.setCalculationSnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of(
                "terms",PayrollTestTerms.german(),"germanBasis",Map.of("priorYear",Map.of("assignmentDate","2025-12-31","pensionBase",1000,"healthBase",150,"source","Bestätigtes SV-Lohnkonto 2025")))));
        String text=render(slip,"v7-german-march-clause.pdf").replaceAll("-\\s*\\R\\s*","-");
        for(String expected:List.of("Märzklausel","2025-12-31","1000 EUR","150 EUR","Bestätigtes SV-Lohnkonto 2025"))assertTrue(text.contains(expected),expected);
    }
    private Payslip fixture(String currency) {
        User user=new User();user.setFirstName("Anna");user.setLastName("Synthetisch");user.setCountry("CHF".equals(currency)?"CH":"DE");
        Payslip slip=new Payslip();slip.setId(101L);slip.setUser(user);slip.setCurrency(currency);
        slip.setPeriodStart(LocalDate.of(2026,3,1));slip.setPeriodEnd(LocalDate.of(2026,3,31));
        slip.setGrossSalary(5500.0);slip.setDeductions(1000.0);slip.setNetSalary(4500.0);slip.setEmployerContributions(700.0);
        slip.setEarnings(List.of(new PayComponent("Base salary",5000.0),new PayComponent("Arrears payment",500.0)));
        slip.setDeductionsList(List.of(new PayComponent("Withholding tax",1000.0)));slip.setEmployerContribList(List.of(new PayComponent("AHV",700.0)));
        return slip;
    }
    private String render(Payslip slip,String filename) throws Exception {
        byte[] pdf=new PdfService().generatePayslipPdfBytes(slip);assertNotNull(pdf);
        PdfReader reader=new PdfReader(pdf);StringBuilder content=new StringBuilder();
        for(int page=1;page<=reader.getNumberOfPages();page++)content.append(PdfTextExtractor.getTextFromPage(reader,page));reader.close();
        Files.createDirectories(Path.of("target/payroll-preview"));Files.write(Path.of("target/payroll-preview",filename),pdf);return content.toString();
    }
    @Test void correctionPdfSeparatesSignedDeltaFromCorrectedMonthlyTotals() throws Exception {
        User user=new User(); user.setFirstName("Anna"); user.setLastName("Korrektur"); user.setCountry("CH");
        Payslip slip=new Payslip(); slip.setId(100L); slip.setUser(user); slip.setCurrency("CHF");
        slip.setPeriodStart(LocalDate.of(2026,6,1)); slip.setPeriodEnd(LocalDate.of(2026,6,30));
        slip.setCorrectionOfId(91L); slip.setSupersedesCorrectionId(99L);
        slip.setGrossSalary(-500.0); slip.setDeductions(-100.0); slip.setNetSalary(-400.0); slip.setEmployerContributions(-50.0);
        slip.setPayoutOvertime(true); slip.setOvertimeHours(-1.5);
        slip.setEarnings(List.of(new PayComponent("Thirteenth salary",-500.0)));
        slip.setDeductionsList(List.of(new PayComponent("Withholding tax",-100.0)));
        slip.setCalculationSnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of(
                "terms",PayrollTestTerms.swiss(),"gross",Map.of("total",7500),"employee",Map.of("Withholding tax",1100),
                "correction",Map.of("reason","Vertrag rückwirkend berichtigt"),
                "swissBasis",Map.of("withholdingAllocations",List.of(Map.of("canton","VD","from","2026-01-01","amount",1100))))));
        byte[] pdf=new PdfService().generatePayslipPdfBytes(slip); assertNotNull(pdf);
        PdfReader reader=new PdfReader(pdf); StringBuilder content=new StringBuilder();
        for(int page=1;page<=reader.getNumberOfPages();page++) {
            String pageText=PdfTextExtractor.getTextFromPage(reader,page);content.append(pageText);
            if(pageText.contains("Nettolohn"))for(String label:List.of("Summe","Bruttolohn","Abzüge"))assertTrue(pageText.contains(label),"Summary split across pages: "+label);
        }
        reader.close(); String text=content.toString();
        for(String value:List.of("Differenzbeleg","#91","#99","Vertrag rückwirkend berichtigt","7500.00 CHF","6400.00 CHF","-400.00","13. Monatslohn","Rückforderungen")) assertTrue(text.contains(value),value);
        for(String value:List.of("korrigierten Monatsstands","nur die Differenzen","VD","2026-01-01","1100.00 CHF")) assertTrue(text.contains(value),value);
        Files.createDirectories(Path.of("target/payroll-preview"));
        Files.write(Path.of("target/payroll-preview/correction.pdf"),pdf);
    }
    @Test void rendersSavedTaxDataAndCurrencyInsteadOfChangedEmployeeFields() throws Exception {
        User user = new User(); user.setFirstName("Anna"); user.setLastName("Test"); user.setCountry("DE");
        user.setBirthDate(LocalDate.of(1980,1,1)); user.setBankAccount("CHANGED-BANK"); user.setSocialSecurityNumber("CHANGED-SSN");
        user.setTrackingBalanceInMinutes(99999);
        Payslip slip = new Payslip(); slip.setId(91L); slip.setUser(user); slip.setCurrency("CHF");
        slip.setPeriodStart(LocalDate.of(2026,1,1)); slip.setPeriodEnd(LocalDate.of(2026,1,31));
        slip.setGrossSalary(5000.0); slip.setDeductions(906.5); slip.setNetSalary(4093.5); slip.setEmployerContributions(5.3);
        slip.setBankAccount("SAVED-BANK"); slip.setSocialSecurityNumber("SAVED-SSN");
        slip.setEarnings(List.of(new PayComponent("Base salary",5000.0)));
        slip.setDeductionsList(List.of(new PayComponent("Withholding tax",276.0)));
        slip.setEmployerContribList(List.of(new PayComponent("AHV administration",5.3)));
        var rules=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("taxMode","CH_SOURCE","canton","ZH","tariffCode","A0N"));
        slip.setCalculationSnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of("terms",rules)));
        byte[] pdf = new PdfService().generatePayslipPdfBytes(slip);
        assertNotNull(pdf);
        PdfReader reader = new PdfReader(pdf);
        StringBuilder content = new StringBuilder();
        for(int page=1;page<=reader.getNumberOfPages();page++) content.append(PdfTextExtractor.getTextFromPage(reader,page));
        reader.close(); String text=content.toString();
        for(String value:List.of("1990-01-01","2025-01-01","ZH A0N","SAVED-BANK","SAVED-SSN","CHF","4093.50","AHV-Verwaltungskosten")) assertTrue(text.contains(value),value);
        for(String value:List.of("CHANGED-BANK","1980-01-01","EUR","Überstundensaldo","Resturlaub")) assertFalse(text.contains(value),value);
        Files.createDirectories(Path.of("target/payroll-preview"));
        Files.write(Path.of("target/payroll-preview/snapshot.pdf"),pdf);
    }
}
