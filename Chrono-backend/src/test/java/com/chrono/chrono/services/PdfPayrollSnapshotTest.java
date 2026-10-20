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
                "correction",Map.of("reason","Vertrag rückwirkend berichtigt"))));
        byte[] pdf=new PdfService().generatePayslipPdfBytes(slip); assertNotNull(pdf);
        PdfReader reader=new PdfReader(pdf); StringBuilder content=new StringBuilder();
        for(int page=1;page<=reader.getNumberOfPages();page++) content.append(PdfTextExtractor.getTextFromPage(reader,page));
        reader.close(); String text=content.toString();
        for(String value:List.of("Differenzbeleg","#91","#99","Vertrag rückwirkend berichtigt","7500.00 CHF","6400.00 CHF","-400.00","13. Monatslohn","Rückforderungen")) assertTrue(text.contains(value),value);
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
