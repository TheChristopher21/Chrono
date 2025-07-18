package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import org.springframework.stereotype.Service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class PdfService {
    public byte[] generatePayslipPdfBytes(Payslip ps) {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Logo if available
            try {
                java.net.URL logoUrl = getClass().getClassLoader().getResource("static/logo.png");
                if (logoUrl != null) {
                    Image logo = Image.getInstance(logoUrl);
                    logo.scaleToFit(120, 60);
                    doc.add(logo);
                }
            } catch (Exception ignore) {
            }

            String companyName = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getName() : "";
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            BaseColor headerBg = BaseColor.LIGHT_GRAY;

            Paragraph title = new Paragraph(companyName + " - Payslip", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            doc.add(title);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(15);

            if (ps.getUser() != null) {
                PdfPCell l1 = new PdfPCell(new Phrase("Employee", labelFont));
                l1.setBackgroundColor(headerBg);
                l1.setPadding(5);
                infoTable.addCell(l1);
                PdfPCell v1 = new PdfPCell(new Phrase(ps.getUser().getFirstName() + " " + ps.getUser().getLastName(), normalFont));
                v1.setPadding(5);
                infoTable.addCell(v1);

                PdfPCell l2 = new PdfPCell(new Phrase("Address", labelFont));
                l2.setBackgroundColor(headerBg);
                l2.setPadding(5);
                infoTable.addCell(l2);
                PdfPCell v2 = new PdfPCell(new Phrase(ps.getUser().getAddress() == null ? "" : ps.getUser().getAddress(), normalFont));
                v2.setPadding(5);
                infoTable.addCell(v2);

                PdfPCell l3 = new PdfPCell(new Phrase("Personnel No", labelFont));
                l3.setBackgroundColor(headerBg);
                l3.setPadding(5);
                infoTable.addCell(l3);
                PdfPCell v3 = new PdfPCell(new Phrase(ps.getUser().getPersonnelNumber() == null ? "" : ps.getUser().getPersonnelNumber(), normalFont));
                v3.setPadding(5);
                infoTable.addCell(v3);
            }

            PdfPCell periodLabel = new PdfPCell(new Phrase("Period", labelFont));
            periodLabel.setBackgroundColor(headerBg);
            periodLabel.setPadding(5);
            infoTable.addCell(periodLabel);
            PdfPCell periodValue = new PdfPCell(new Phrase(ps.getPeriodStart() + " - " + ps.getPeriodEnd(), normalFont));
            periodValue.setPadding(5);
            infoTable.addCell(periodValue);
            doc.add(infoTable);
            doc.add(new Paragraph(" "));

            PdfPTable earningsTable = new PdfPTable(new float[]{4, 2});
            earningsTable.setWidthPercentage(100);
            earningsTable.setSpacingAfter(10);
            PdfPCell eHead1 = new PdfPCell(new Phrase("Earnings", labelFont));
            eHead1.setBackgroundColor(headerBg);
            eHead1.setPadding(5);
            earningsTable.addCell(eHead1);
            PdfPCell eHead2 = new PdfPCell(new Phrase("Amount", labelFont));
            eHead2.setBackgroundColor(headerBg);
            eHead2.setPadding(5);
            earningsTable.addCell(eHead2);
            for (var comp : ps.getEarnings()) {
                PdfPCell type = new PdfPCell(new Phrase(comp.getType(), normalFont));
                type.setPadding(5);
                earningsTable.addCell(type);
                PdfPCell amount = new PdfPCell(new Phrase(String.format("CHF %.2f", comp.getAmount()), normalFont));
                amount.setPadding(5);
                earningsTable.addCell(amount);
            }
            doc.add(earningsTable);

            PdfPTable dedTable = new PdfPTable(new float[]{4, 2});
            dedTable.setWidthPercentage(100);
            dedTable.setSpacingAfter(10);
            PdfPCell dHead1 = new PdfPCell(new Phrase("Deductions", labelFont));
            dHead1.setBackgroundColor(headerBg);
            dHead1.setPadding(5);
            dedTable.addCell(dHead1);
            PdfPCell dHead2 = new PdfPCell(new Phrase("Amount", labelFont));
            dHead2.setBackgroundColor(headerBg);
            dHead2.setPadding(5);
            dedTable.addCell(dHead2);
            for (var comp : ps.getDeductionsList()) {
                PdfPCell type = new PdfPCell(new Phrase(comp.getType(), normalFont));
                type.setPadding(5);
                dedTable.addCell(type);
                PdfPCell amount = new PdfPCell(new Phrase(String.format("CHF %.2f", comp.getAmount()), normalFont));
                amount.setPadding(5);
                dedTable.addCell(amount);
            }
            doc.add(dedTable);
            doc.add(new Paragraph(" "));

            PdfPTable totals = new PdfPTable(new float[]{4, 2});
            totals.setWidthPercentage(100);
            totals.setSpacingAfter(20);
            PdfPCell tHead1 = new PdfPCell(new Phrase("Gross Salary", labelFont));
            tHead1.setBackgroundColor(headerBg);
            tHead1.setPadding(5);
            totals.addCell(tHead1);
            PdfPCell tVal1 = new PdfPCell(new Phrase(String.format("CHF %.2f", ps.getGrossSalary()), normalFont));
            tVal1.setPadding(5);
            totals.addCell(tVal1);

            PdfPCell tHead2 = new PdfPCell(new Phrase("Deductions", labelFont));
            tHead2.setBackgroundColor(headerBg);
            tHead2.setPadding(5);
            totals.addCell(tHead2);
            PdfPCell tVal2 = new PdfPCell(new Phrase(String.format("CHF %.2f", ps.getDeductions()), normalFont));
            tVal2.setPadding(5);
            totals.addCell(tVal2);

            PdfPCell tHead3 = new PdfPCell(new Phrase("Net Salary", labelFont));
            tHead3.setBackgroundColor(headerBg);
            tHead3.setPadding(5);
            totals.addCell(tHead3);
            PdfPCell tVal3 = new PdfPCell(new Phrase(String.format("CHF %.2f", ps.getNetSalary()), normalFont));
            tVal3.setPadding(5);
            totals.addCell(tVal3);
            doc.add(totals);

            if (ps.getPayoutDate() != null) {
                Paragraph payout = new Paragraph("Payout date: " + ps.getPayoutDate(), normalFont);
                payout.setSpacingAfter(10);
                doc.add(payout);
            }

            Paragraph footer = new Paragraph("Generated by Chrono Payroll", FontFactory.getFont(FontFactory.HELVETICA, 8));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);
            doc.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            return null;
        }
    }

    public String generatePayslipPdf(Payslip ps) {
        String path = "/tmp/payslip-" + ps.getId() + ".pdf";
        byte[] data = generatePayslipPdfBytes(ps);
        if (data == null) return null;
        try {
            java.nio.file.Files.write(java.nio.file.Path.of(path), data);
        } catch (IOException e) {
            return null;
        }
        return path;
    }
}
