package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.ColumnText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import com.chrono.chrono.services.VacationService;

import java.io.IOException;

@Service
public class PdfService {
    @Autowired
    private VacationService vacationService;

    public byte[] generatePayslipPdfBytes(Payslip ps) {
        return generatePayslipPdfBytes(ps, "de");
    }

    public byte[] generatePayslipPdfBytes(Payslip ps, String lang) {
        Document doc = new Document(PageSize.A4, 36, 36, 48, 36);
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PageNumberEvent());
            doc.open();

            boolean en = "en".equalsIgnoreCase(lang);
            String titleText = en ? "Payslip" : "Lohnabrechnung";
            String employeeLabel = en ? "Employee" : "Mitarbeiter";
            String personnelLabel = en ? "Personnel no." : "Personalnummer";
            String addressLabel = en ? "Address" : "Adresse";
            String birthLabel = en ? "Birthdate" : "Geburtsdatum";
            String entryLabel = en ? "Entry" : "Eintritt";
            String ahvLabel = en ? "AHV no." : "AHV-Nr.";
            String bankLabel = en ? "Bank" : "Bank";
            String deptLabel = en ? "Department" : "Abteilung";
            String nationalityLabel = en ? "Nationality" : "Nationalität";
            String civilLabel = en ? "Marital status" : "Zivilstand";
            String childrenLabel = en ? "Children" : "Kinder";
            String religionLabel = en ? "Religion" : "Religion";
            String pensumLabel = en ? "Workload" : "Pensum";
            String taxLabel = en ? "Withholding tax" : "Quellensteuer";
            String periodLabel = en ? "Payroll period" : "Abrechnungsmonat";
            String earningsHeader = en ? "Earnings" : "Bezüge";
            String amountHeader = en ? "Amount" : "Betrag";
            String currencyHeader = en ? "Currency" : "Währung";
            String deductionsHeader = en ? "Deductions" : "Abzüge";
            String employerHeader = en ? "Employer contribution" : "Arbeitgeberbeitrag";
            String overtimeLabel = en ? "Overtime balance" : "Überstundensaldo";
            String vacationLabel = en ? "Remaining vacation" : "Resturlaub";
            String totalsHeader = en ? "Total" : "Summe";
            String grossLabel = en ? "Gross salary" : "Bruttolohn";
            String deductionsLabel = en ? "Deductions" : "Abzüge";
            String netLabel = en ? "Net salary" : "Nettolohn";
            String payoutPrefix = en ? "Payout date: " : "Auszahlungsdatum: ";
            String legalText = en ? "This document was generated electronically and is valid without a signature." : "Dieses Dokument wurde maschinell erstellt und ist ohne Unterschrift gültig.";
            String contactText = en ? "If you have any questions, please contact your HR department." : "Bei Fragen wenden Sie sich bitte an Ihre Personalabteilung.";
            String signatureText = en ? "  Employer                   Employee" : "  Arbeitgeber                Mitarbeiter";
            String pensionLabel = en ? "Pension fund" : "Pensionskasse";

            java.util.Map<String, String> compTrans = new java.util.HashMap<>();
            compTrans.put("Base salary", en ? "Base salary" : "Grundlohn");
            compTrans.put("Overtime", en ? "Overtime" : "Überstunden");
            compTrans.put("Tax", en ? "Tax" : "Steuer");
            compTrans.put("Social", en ? "Social" : "Sozialabgaben");


            // ---- Header mit Firmenlogo und Firmendaten ----
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{2, 4});

            // Logo (links)
            try {
                String logoPath = null;
                if (ps.getUser() != null && ps.getUser().getCompany() != null) {
                    logoPath = ps.getUser().getCompany().getLogoPath();
                }
                Image logo = null;
                if (logoPath != null && java.nio.file.Files.exists(java.nio.file.Path.of(logoPath))) {
                    logo = Image.getInstance(logoPath);
                } else {
                    java.net.URL logoUrl = getClass().getClassLoader().getResource("static/logo.png");
                    if (logoUrl != null) {
                        logo = Image.getInstance(logoUrl);
                    }
                }
                if (logo != null) {
                    logo.scaleToFit(110, 50);
                    PdfPCell logoCell = new PdfPCell(logo, false);
                    logoCell.setBorder(Rectangle.NO_BORDER);
                    header.addCell(logoCell);
                } else {
                    PdfPCell logoCell = new PdfPCell(new Phrase(""));
                    logoCell.setBorder(Rectangle.NO_BORDER);
                    header.addCell(logoCell);
                }
            } catch (Exception e) {
                header.addCell(new PdfPCell(new Phrase("")));
            }

            // Firmendaten (rechts)
            String companyName = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getName() : "";
            String address1 = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getAddressLine1() : null;
            String address2 = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getAddressLine2() : null;
            String postal = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getPostalCode() : null;
            String city = ps.getUser() != null && ps.getUser().getCompany() != null
                    ? ps.getUser().getCompany().getCity() : null;

            PdfPCell companyCell = new PdfPCell();
            companyCell.addElement(new Phrase(companyName,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
            if (address1 != null && !address1.isBlank())
                companyCell.addElement(new Phrase(address1, FontFactory.getFont(FontFactory.HELVETICA, 10)));
            if (address2 != null && !address2.isBlank())
                companyCell.addElement(new Phrase(address2, FontFactory.getFont(FontFactory.HELVETICA, 10)));
            String plzOrt = ((postal != null ? postal : "") + " " + (city != null ? city : "")).trim();
            if (!plzOrt.isBlank())
                companyCell.addElement(new Phrase(plzOrt, FontFactory.getFont(FontFactory.HELVETICA, 10)));
            companyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            companyCell.setVerticalAlignment(Element.ALIGN_TOP);
            companyCell.setBorder(Rectangle.NO_BORDER);
            header.addCell(companyCell);
            doc.add(header);

            doc.add(new Paragraph(" "));


            // ---- Dokumenttitel ----
            Paragraph title = new Paragraph(titleText, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(18);
            doc.add(title);

            // ---- Mitarbeiterdatenblock ----
            PdfPTable employeeTable = new PdfPTable(4);
            employeeTable.setWidthPercentage(100);
            employeeTable.setWidths(new float[]{2, 4, 2, 4});
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            employeeTable.addCell(cell(employeeLabel, labelFont, true));
            employeeTable.addCell(cell(ps.getUser().getFirstName() + " " + ps.getUser().getLastName(), normalFont, false));
            employeeTable.addCell(cell(personnelLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getPersonnelNumber()), normalFont, false));

            employeeTable.addCell(cell(addressLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getAddress()), normalFont, false));
            employeeTable.addCell(cell(birthLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getBirthDate()), normalFont, false));

            employeeTable.addCell(cell(entryLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getEntryDate()), normalFont, false));
            employeeTable.addCell(cell(ahvLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getSocialSecurityNumber()), normalFont, false));


            employeeTable.addCell(cell(bankLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getBankAccount()), normalFont, false));
            // Das User-Entity besitzt kein 'Department'-Feld mehr. Zur
            // Wahrung des Layouts wird ein leerer Wert eingetragen.
            employeeTable.addCell(cell(deptLabel, labelFont, true));
            employeeTable.addCell(cell("", normalFont, false));

            employeeTable.addCell(cell(nationalityLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getCountry()), normalFont, false));
            employeeTable.addCell(cell(civilLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getCivilStatus()), normalFont, false));

            employeeTable.addCell(cell(childrenLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getChildren()), normalFont, false));
            employeeTable.addCell(cell(religionLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getReligion()), normalFont, false));

            employeeTable.addCell(cell(pensumLabel, labelFont, true));
            employeeTable.addCell(cell(ps.getUser().getWorkPercentage() + "%", normalFont, false));
            employeeTable.addCell(cell(taxLabel, labelFont, true));
            String taxInfo = ps.getUser().getTarifCode() != null && !ps.getUser().getTarifCode().isEmpty() ?
                    ps.getUser().getTarifCode() : safe(ps.getUser().getTaxClass());
            employeeTable.addCell(cell(taxInfo, normalFont, false));


            doc.add(employeeTable);
            doc.add(new Paragraph(" "));

            // ---- Abrechnungszeitraum ----
            PdfPTable periodTable = new PdfPTable(2);
            periodTable.setWidthPercentage(55);
            periodTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            periodTable.addCell(cell(periodLabel, labelFont, true));
            periodTable.addCell(cell(ps.getPeriodStart() + " – " + ps.getPeriodEnd(), normalFont, false));
            doc.add(periodTable);

            doc.add(new Paragraph(" "));

            // ---- Verdienst (Earnings) ----
            PdfPTable earningsTable = new PdfPTable(new float[]{4, 2, 2});
            earningsTable.setWidthPercentage(70);
            earningsTable.setSpacingAfter(10);
            earningsTable.addCell(headerCell(earningsHeader));
            earningsTable.addCell(headerCell(amountHeader));
            earningsTable.addCell(headerCell(currencyHeader));
            for (var comp : ps.getEarnings()) {
                String etype = comp.getType();
                if (!en && compTrans.containsKey(etype)) {
                    etype = compTrans.get(etype);
                }
                earningsTable.addCell(cell(etype, normalFont, false));
                earningsTable.addCell(cell(String.format("%.2f", comp.getAmount()), normalFont, false));
                earningsTable.addCell(cell("CHF", normalFont, false));

            }
            doc.add(earningsTable);

            // ---- Abzüge (Deductions) ----
            PdfPTable dedTable = new PdfPTable(new float[]{4, 2, 2});
            dedTable.setWidthPercentage(70);
            dedTable.setSpacingAfter(10);
            dedTable.addCell(headerCell(deductionsHeader));
            dedTable.addCell(headerCell(amountHeader));
            dedTable.addCell(headerCell(currencyHeader));
            for (var comp : ps.getDeductionsList()) {
                String dtype = comp.getType();
                if (!en && compTrans.containsKey(dtype)) {
                    dtype = compTrans.get(dtype);
                }
                dedTable.addCell(cell(dtype, normalFont, false));
                dedTable.addCell(cell(String.format("%.2f", comp.getAmount()), normalFont, false));
                dedTable.addCell(cell("CHF", normalFont, false));

            }
            doc.add(dedTable);
            doc.add(new Paragraph(" "));

            if (ps.getEmployerContributions() != null) {
                PdfPTable empTable = new PdfPTable(new float[]{4,2,2});
                empTable.setWidthPercentage(70);
                empTable.setSpacingAfter(10);
                empTable.addCell(headerCell(employerHeader));
                empTable.addCell(headerCell(amountHeader));
                empTable.addCell(headerCell(currencyHeader));
                empTable.addCell(cell(pensionLabel, normalFont, false));

                empTable.addCell(cell(String.format("%.2f", ps.getEmployerContributions()), normalFont, false));
                empTable.addCell(cell("CHF", normalFont, false));
                doc.add(empTable);
                doc.add(new Paragraph(" "));
            }

            // ---- Überstunden- und Urlaubssaldo ----
            int overtimeMinutes = ps.getUser().getTrackingBalanceInMinutes() != null
                    ? ps.getUser().getTrackingBalanceInMinutes() : 0;
            double overtimeHours = overtimeMinutes / 60.0;
            double vacationDays = vacationService.calculateRemainingVacationDays(
                    ps.getUser().getUsername(),
                    ps.getPeriodEnd() != null ? ps.getPeriodEnd().getYear() : LocalDate.now().getYear());

            if (overtimeMinutes != 0 || vacationDays > 0) {
                PdfPTable saldoTable = new PdfPTable(2);
                saldoTable.setWidthPercentage(55);
                saldoTable.setSpacingAfter(8);
                if (overtimeMinutes != 0) {
                    saldoTable.addCell(cell(overtimeLabel, labelFont, true));
                    saldoTable.addCell(cell(String.format("%.1f Std.", overtimeHours), normalFont, false));
                }
                if (vacationDays > 0) {
                    saldoTable.addCell(cell(vacationLabel, labelFont, true));
                    saldoTable.addCell(cell(String.format("%.1f Tage", vacationDays), normalFont, false));
                }
                doc.add(saldoTable);
            }


            // ---- Summenblock ----
            PdfPTable totals = new PdfPTable(3);
            totals.setWidthPercentage(70);
            totals.setSpacingAfter(15);
            totals.addCell(headerCell(totalsHeader, BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell(amountHeader, BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell(currencyHeader, BaseColor.LIGHT_GRAY));

            totals.addCell(cell(grossLabel, normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getGrossSalary()), normalFont, false));
            totals.addCell(cell("CHF", normalFont, false));

            totals.addCell(cell(deductionsLabel, normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getDeductions()), normalFont, false));
            totals.addCell(cell("CHF", normalFont, false));

            totals.addCell(cell(netLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell(String.format("%.2f", ps.getNetSalary()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell("CHF", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));


            doc.add(totals);

            // ---- Auszahlungsdatum ----
            if (ps.getPayoutDate() != null) {
                Paragraph payout = new Paragraph(payoutPrefix + ps.getPayoutDate(), normalFont);
                payout.setSpacingAfter(8);
                doc.add(payout);
            }

            // ---- Rechtlicher Footer und Unterschrift ----
            doc.add(new Paragraph(" "));

            Paragraph legal = new Paragraph(legalText,
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY));
            legal.setAlignment(Element.ALIGN_LEFT);
            legal.setSpacingAfter(6);
            doc.add(legal);

            Paragraph contact = new Paragraph(contactText,
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY));
            contact.setAlignment(Element.ALIGN_LEFT);
            doc.add(contact);

            doc.add(new Paragraph("\n\n"));
            doc.add(new Paragraph("______________________________           ______________________________", normalFont));
            doc.add(new Paragraph(signatureText,
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
            BarcodeQRCode barcode = new BarcodeQRCode("payslip-" + ps.getId(), 100, 100, null);
            Image qrImage = barcode.getImage();
            qrImage.scaleToFit(70, 70);
            qrImage.setAlignment(Image.ALIGN_RIGHT);
            doc.add(qrImage);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public String generatePayslipPdf(Payslip ps) {
        return generatePayslipPdf(ps, "de");
    }

    public String generatePayslipPdf(Payslip ps, String lang) {
        String path = "/tmp/payslip-" + ps.getId() + ".pdf";
        byte[] data = generatePayslipPdfBytes(ps, lang);
        if (data == null) return null;
        try {
            java.nio.file.Files.write(java.nio.file.Path.of(path), data);
        } catch (IOException e) {
            return null;
        }
        return path;
    }

    // Hilfsfunktion für Zellen mit/ohne Kopf-Design
    private static PdfPCell cell(String text, Font font, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(5);
        if (header) {
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        }
        return cell;
    }

    private static PdfPCell headerCell(String text) {
        return headerCell(text, BaseColor.LIGHT_GRAY);
    }

    private static PdfPCell headerCell(String text, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        return cell;
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static class PageNumberEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(String.valueOf(writer.getPageNumber()),
                            FontFactory.getFont(FontFactory.HELVETICA, 8)),
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 20, 0);
        }
    }
}
