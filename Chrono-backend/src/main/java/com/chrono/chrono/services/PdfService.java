package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PdfService {
    public byte[] generatePayslipPdfBytes(Payslip ps) {
        Document doc = new Document(PageSize.A4, 36, 36, 48, 36);
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ---- Header mit Firmenlogo und Firmendaten ----
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{2, 4});

            // Logo (links)
            try {
                java.net.URL logoUrl = getClass().getClassLoader().getResource("static/logo.png");
                if (logoUrl != null) {
                    Image logo = Image.getInstance(logoUrl);
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
                    ? ps.getUser().getCompany().getName() : "Firma Muster AG";
            // Company besitzt aktuell kein Adressfeld. Verwende statischen
            // Platzhalter, um Kompilierungsfehler zu vermeiden.
            String companyAddress = "Beispielstr. 1, 8000 Zürich";
            String companyContact = "Tel. 044 123 45 67 | info@firma.ch";
            PdfPCell companyCell = new PdfPCell();
            companyCell.addElement(new Phrase(companyName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
            companyCell.addElement(new Phrase(companyAddress, FontFactory.getFont(FontFactory.HELVETICA, 10)));
            companyCell.addElement(new Phrase(companyContact, FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.DARK_GRAY)));
            companyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            companyCell.setVerticalAlignment(Element.ALIGN_TOP);
            companyCell.setBorder(Rectangle.NO_BORDER);
            header.addCell(companyCell);
            doc.add(header);

            doc.add(new Paragraph(" "));


            // ---- Dokumenttitel ----
            Paragraph title = new Paragraph("Lohnabrechnung / Payslip", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(18);
            doc.add(title);

            // ---- Mitarbeiterdatenblock ----
            PdfPTable employeeTable = new PdfPTable(4);
            employeeTable.setWidthPercentage(100);
            employeeTable.setWidths(new float[]{2, 4, 2, 4});
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            employeeTable.addCell(cell("Mitarbeiter", labelFont, true));
            employeeTable.addCell(cell(ps.getUser().getFirstName() + " " + ps.getUser().getLastName(), normalFont, false));
            employeeTable.addCell(cell("Personalnummer", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getPersonnelNumber()), normalFont, false));

            employeeTable.addCell(cell("Adresse", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getAddress()), normalFont, false));
            employeeTable.addCell(cell("Geburtsdatum", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getBirthDate()), normalFont, false));

            employeeTable.addCell(cell("Eintritt", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getEntryDate()), normalFont, false));
            employeeTable.addCell(cell("AHV-Nr.", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getSocialSecurityNumber()), normalFont, false));


            employeeTable.addCell(cell("Bank", labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getBankAccount()), normalFont, false));
            // Das User-Entity besitzt kein 'Department'-Feld mehr. Zur
            // Wahrung des Layouts wird ein leerer Wert eingetragen.
            employeeTable.addCell(cell("Abteilung", labelFont, true));
            employeeTable.addCell(cell("", normalFont, false));

            doc.add(employeeTable);
            doc.add(new Paragraph(" "));

            // ---- Abrechnungszeitraum ----
            PdfPTable periodTable = new PdfPTable(2);
            periodTable.setWidthPercentage(55);
            periodTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            periodTable.addCell(cell("Abrechnungsmonat", labelFont, true));
            periodTable.addCell(cell(ps.getPeriodStart() + " – " + ps.getPeriodEnd(), normalFont, false));
            doc.add(periodTable);

            doc.add(new Paragraph(" "));

            // ---- Verdienst (Earnings) ----
            PdfPTable earningsTable = new PdfPTable(new float[]{4, 2, 2});
            earningsTable.setWidthPercentage(70);
            earningsTable.setSpacingAfter(10);
            earningsTable.addCell(headerCell("Bezüge / Earnings"));
            earningsTable.addCell(headerCell("Betrag"));
            earningsTable.addCell(headerCell("Währung"));
            for (var comp : ps.getEarnings()) {
                earningsTable.addCell(cell(comp.getType(), normalFont, false));
                earningsTable.addCell(cell(String.format("%.2f", comp.getAmount()), normalFont, false));
                earningsTable.addCell(cell("CHF", normalFont, false));

            }
            doc.add(earningsTable);

            // ---- Abzüge (Deductions) ----
            PdfPTable dedTable = new PdfPTable(new float[]{4, 2, 2});
            dedTable.setWidthPercentage(70);
            dedTable.setSpacingAfter(10);
            dedTable.addCell(headerCell("Abzüge / Deductions"));
            dedTable.addCell(headerCell("Betrag"));
            dedTable.addCell(headerCell("Währung"));
            for (var comp : ps.getDeductionsList()) {
                dedTable.addCell(cell(comp.getType(), normalFont, false));
                dedTable.addCell(cell(String.format("%.2f", comp.getAmount()), normalFont, false));
                dedTable.addCell(cell("CHF", normalFont, false));

            }
            doc.add(dedTable);
            doc.add(new Paragraph(" "));

            // Die Felder "OvertimeBalance" und "VacationBalance" existieren im
            // User-Entity nicht mehr. Der entsprechende PDF-Block wird daher
            // übersprungen.

            // ---- Summenblock ----
            PdfPTable totals = new PdfPTable(3);
            totals.setWidthPercentage(70);
            totals.setSpacingAfter(15);
            totals.addCell(headerCell("Summe", BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell("Betrag", BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell("Währung", BaseColor.LIGHT_GRAY));

            totals.addCell(cell("Bruttolohn", normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getGrossSalary()), normalFont, false));
            totals.addCell(cell("CHF", normalFont, false));

            totals.addCell(cell("Abzüge", normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getDeductions()), normalFont, false));
            totals.addCell(cell("CHF", normalFont, false));

            totals.addCell(cell("Nettolohn", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell(String.format("%.2f", ps.getNetSalary()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell("CHF", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));


            doc.add(totals);

            // ---- Auszahlungsdatum ----
            if (ps.getPayoutDate() != null) {
                Paragraph payout = new Paragraph("Auszahlungsdatum: " + ps.getPayoutDate(), normalFont);
                payout.setSpacingAfter(8);
                doc.add(payout);
            }

            // ---- Rechtlicher Footer und Unterschrift ----
            doc.add(new Paragraph(" "));

            Paragraph legal = new Paragraph("Dieses Dokument wurde maschinell erstellt und ist ohne Unterschrift gültig.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY));
            legal.setAlignment(Element.ALIGN_LEFT);
            legal.setSpacingAfter(6);
            doc.add(legal);

            Paragraph contact = new Paragraph("Bei Fragen wenden Sie sich bitte an Ihre Personalabteilung.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY));
            contact.setAlignment(Element.ALIGN_LEFT);
            doc.add(contact);

            doc.add(new Paragraph("\n\n"));
            doc.add(new Paragraph("______________________________           ______________________________", normalFont));
            doc.add(new Paragraph("  Arbeitgeber / Employer                         Mitarbeiter / Employee",
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
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
}
