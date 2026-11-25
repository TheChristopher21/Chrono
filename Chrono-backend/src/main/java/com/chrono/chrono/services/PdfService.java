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
            com.chrono.chrono.services.payroll.PayrollTerms savedTerms = null;
            if (ps.getCalculationSnapshot() != null) {
                var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
                savedTerms = json.treeToValue(json.readTree(ps.getCalculationSnapshot()).get("terms"), com.chrono.chrono.services.payroll.PayrollTerms.class);
            }
            String currency = ps.getCurrency() != null ? ps.getCurrency()
                    : ps.getUser() != null && "DE".equalsIgnoreCase(ps.getUser().getCountry()) ? "EUR" : "CHF";
            String titleText = en ? "Payslip" : "Lohnabrechnung";
            if (ps.isCorrection()) titleText = en ? "Payroll correction" : "Lohnkorrektur – Differenzbeleg";
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
            if (savedTerms != null && "DE".equals(savedTerms.country())) taxLabel = en ? "Tax class" : "Steuerklasse";
            String periodLabel = en ? "Payroll period" : "Abrechnungsmonat";
            String earningsHeader = en ? "Earnings" : "Bezüge";
            String amountHeader = en ? "Amount" : "Betrag";
            String currencyHeader = en ? "Currency" : "Währung";
            String deductionsHeader = en ? "Deductions" : "Abzüge";
            String employerHeader = en ? "Employer contribution" : "Arbeitgeberbeitrag";
            String overtimeLabel = en ? "Overtime balance" : "Überstundensaldo";
            String billedOvertimeLabel = en ? "Billed overtime" : "Abgerechnete Überstunden";
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
            compTrans.put("Thirteenth salary", en ? "Thirteenth salary" : "13. Monatslohn");
            compTrans.put("Paid vacation", en ? "Paid vacation" : "Ferien-/Urlaubslohn");
            compTrans.put("Paid public holidays", en ? "Paid public holidays" : "Bezahlte Feiertage");
            compTrans.put("Employer sick pay", en ? "Employer sick pay" : "Arbeitgeber-Lohnfortzahlung Krankheit");
            compTrans.put("Vacation allowance", en ? "Vacation allowance" : "Ferienzuschlag");
            compTrans.put("Holiday allowance", en ? "Holiday allowance" : "Feiertagszuschlag");
            compTrans.put("Legacy net reconciliation", en ? "Legacy net reconciliation" : "Ausgleich Nettodifferenz Altbeleg");
            compTrans.put("Legacy deduction difference", en ? "Legacy deduction difference" : "Abzugsdifferenz Altbeleg");
            compTrans.put("Legacy earnings difference", en ? "Legacy earnings difference" : "Bruttodifferenz Altbeleg");
            compTrans.put("Legacy employer difference", en ? "Legacy employer difference" : "Beitragsdifferenz Altbeleg");
            compTrans.put("Overtime", en ? "Overtime" : "Überstunden");
            compTrans.put("Overtime payout", en ? "Overtime payout" : "Überstundenauszahlung");
            compTrans.put("Overtime premium", en ? "Overtime premium" : "Überstundenzuschlag");
            compTrans.put("AHV administration", en ? "AHV administration" : "AHV-Verwaltungskosten");
            compTrans.put("Tax", en ? "Tax" : "Steuer");
            compTrans.put("Social", en ? "Social" : "Sozialabgaben");
            compTrans.put("Income tax", en ? "Income tax" : "Einkommensteuer");
            compTrans.put("Solidarity surcharge", en ? "Solidarity surcharge" : "Solidaritätszuschlag");
            compTrans.put("Church tax", en ? "Church tax" : "Kirchensteuer");
            compTrans.put("Pension insurance", en ? "Pension insurance" : "Rentenversicherung");
            compTrans.put("Health insurance", en ? "Health insurance" : "Krankenversicherung");
            compTrans.put("Nursing insurance", en ? "Nursing insurance" : "Pflegeversicherung");
            compTrans.put("Unemployment insurance", en ? "Unemployment insurance" : "Arbeitslosenversicherung");
            compTrans.put("Insolvency levy", en ? "Insolvency levy" : "Insolvenzgeldumlage");
            compTrans.put("AHV/IV/EO", "AHV/IV/EO");
            compTrans.put("ALV", "ALV");
            compTrans.put("BVG", "BVG");
            compTrans.put("NBU", "NBU");
            compTrans.put("KTG", "KTG");
            compTrans.put("Withholding tax", en ? "Withholding tax" : "Quellensteuer");
            compTrans.put("BU", "BU");
            compTrans.put("FAK", "FAK");
            for (String key : java.util.List.of("Income tax", "Solidarity surcharge", "Church tax", "Pension insurance", "Health insurance", "Nursing insurance", "Unemployment insurance", "Insolvency levy"))
                compTrans.put(key + " · thirteenth", compTrans.get(key) + (en ? " · thirteenth salary" : " · 13. Monatslohn"));
            compTrans.put("Arrears payment",en?"Arrears / supplementary payment":"Lohnnachzahlung / zusätzlicher Bezug");
            compTrans.put("BVG · Nachzahlung",en?"BVG · supplementary contribution":"BVG · Nachzahlung");
            for(String key:java.util.List.of("Income tax","Solidarity surcharge","Church tax","Pension insurance","Health insurance","Nursing insurance","Unemployment insurance","Insolvency levy")) {
                compTrans.put(key+" · one-off remuneration",compTrans.get(key)+(en?" · one-off remuneration":" · sonstiger Bezug"));
                compTrans.put(key+" · SV 2025",compTrans.get(key)+" · SV 2025");
            }
            for(var component:java.util.stream.Stream.of(ps.getDeductionsList(),ps.getEmployerContribList()).filter(java.util.Objects::nonNull).flatMap(java.util.Collection::stream).toList()) {
                String type=component.getType();if(type==null)continue;int at=type.indexOf(" · Altperiode ");if(at>0){String key=type.substring(0,at);compTrans.put(type,compTrans.getOrDefault(key,key)+(en?" · original period ":" · Altperiode ")+type.substring(at+14));}
            }


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
            employeeTable.setWidths(new float[]{3, 4, 3, 4});
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            employeeTable.addCell(cell(employeeLabel, labelFont, true));
            employeeTable.addCell(cell(ps.getUser().getFirstName() + " " + ps.getUser().getLastName(), normalFont, false));
            employeeTable.addCell(cell(personnelLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getPersonnelNumber()), normalFont, false));

            employeeTable.addCell(cell(addressLabel, labelFont, true));
            employeeTable.addCell(cell(safe(ps.getUser().getAddress()), normalFont, false));
            employeeTable.addCell(cell(birthLabel, labelFont, true));
            employeeTable.addCell(cell(safe(savedTerms != null ? savedTerms.birthDate() : ps.getUser().getBirthDate()), normalFont, false));

            employeeTable.addCell(cell(entryLabel, labelFont, true));
            employeeTable.addCell(cell(safe(savedTerms != null ? savedTerms.entryDate() : ps.getUser().getEntryDate()), normalFont, false));
            employeeTable.addCell(cell(ahvLabel, labelFont, true));
            employeeTable.addCell(cell(safe(savedTerms != null ? ps.getSocialSecurityNumber() : ps.getUser().getSocialSecurityNumber()), normalFont, false));


            employeeTable.addCell(cell(bankLabel, labelFont, true));
            employeeTable.addCell(cell(safe(savedTerms != null ? ps.getBankAccount() : ps.getUser().getBankAccount()), normalFont, false));
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
            if (savedTerms != null) taxInfo = "CH_SOURCE".equals(savedTerms.taxMode()) ? savedTerms.canton() + " " + savedTerms.tariffCode()
                    : "DE".equals(savedTerms.country()) ? safe(savedTerms.taxClass()) : (en ? "Ordinary taxation" : "Ordentliche Besteuerung");
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
            if (ps.isCorrection()) {
                var saved = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readTree(ps.getCalculationSnapshot());
                doc.add(new Paragraph((en ? "Original document #" : "Originalbeleg #") + ps.getCorrectionOfId()
                        + (ps.getSupersedesCorrectionId() == null ? "" : (en ? "; previous correction #" : "; vorherige Korrektur #") + ps.getSupersedesCorrectionId()), normalFont));
                doc.add(new Paragraph(saved.path("correction").path("reason").asText(), normalFont));
                if(!saved.path("correction").path("yearAssignment").isMissingNode()&&!saved.path("correction").path("yearAssignment").isNull()) {
                    doc.add(new Paragraph((en?"Original-period payment evidence: ":"Nachweis der Originalperiodenzahlung: ")+saved.path("correction").path("yearAssignment").path("source").asText(),normalFont));
                    if(ps.getPayoutDate()!=null&&ps.getPayoutDate().getYear()!=ps.getPeriodStart().getYear())doc.add(new Paragraph((en?"Tax/social-insurance correction for original year ":"Steuer-/SV-Berichtigung des Originaljahrs ")+ps.getPeriodStart().getYear()+(en?"; gross wages unchanged.":"; Bruttolohn unverändert."),normalFont));
                }
                var dto = new com.chrono.chrono.dto.PayslipDTO(ps);
                doc.add(new Paragraph((en ? "Corrected monthly totals: gross " : "Korrigierter Monatsstand: Brutto ") + String.format(java.util.Locale.ROOT, "%.2f %s", dto.getCorrectedGrossSalary(), currency)
                        + (en ? ", net " : ", Netto ") + String.format(java.util.Locale.ROOT, "%.2f %s", dto.getCorrectedNetSalary(), currency), normalFont));
                doc.add(new Paragraph(en ? "Only differences are shown. Negative net amounts are repayments/offsets; original documents remain valid."
                        : "Ausgewiesen sind ausschliesslich Differenzen. Negative Nettobeträge sind Rückforderungen/Verrechnungen; Originalbelege bleiben erhalten.", normalFont));
            }
            if(ps.getCalculationSnapshot()!=null) {
                var snapshotJson=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
                var saved=snapshotJson.readTree(ps.getCalculationSnapshot());
                var remuneration=saved.path("remuneration");
                if(remuneration.isObject()&&remuneration.path("applied").isArray()&&!remuneration.path("applied").isEmpty()) {
                    doc.add(new Paragraph(en?"Allocation of supplementary payments":"Zuordnung der Nachzahlungen",labelFont));
                    doc.add(new Paragraph(en?"The amounts below are calculation bases. The earnings/deduction tables show amounts charged or paid.":"Die folgenden Beträge sind Berechnungsgrundlagen. Ausbezahlte bzw. verrechnete Beträge stehen in den Bezüge-/Abzugstabellen.",normalFont));
                    PdfPTable bases=new PdfPTable(new float[]{3,2});bases.setWidthPercentage(100);bases.setSpacingBefore(6);bases.setSpacingAfter(6);bases.setKeepTogether(true);
                    for(String[] item:new String[][]{{en?"Gross cash in this period":"Bruttozufluss dieser Periode",saved.path("gross").path("total").asText()},{en?"Gross allocated for withholding":"Steuerlich zugeordnetes Brutto",remuneration.path("taxGross").path("total").asText()},{en?"Gross allocated for social insurance":"Der SV-Periode zugeordnetes Brutto",remuneration.path("socialGross").path("total").asText()}}) {
                        bases.addCell(cell(item[0],normalFont,false));bases.addCell(cell(item[1]+" "+currency,normalFont,false));
                    }
                    doc.add(bases);java.util.Set<String> shown=new java.util.HashSet<>();
                    for(var applied:remuneration.path("applied")) {
                        var payment=applied.path("payment");if(!shown.add(payment.path("reference").asText()))continue;
                        String from=snapshotJson.treeToValue(payment.path("earnedFrom"),java.time.LocalDate.class).toString(),through=snapshotJson.treeToValue(payment.path("earnedThrough"),java.time.LocalDate.class).toString();
                        String received=snapshotJson.treeToValue(payment.path("receivedOn"),java.time.LocalDate.class).toString(),claim=snapshotJson.treeToValue(payment.path("claimEstablishedOn"),java.time.LocalDate.class).toString();
                        doc.add(new Paragraph(payment.path("reference").asText()+": "+payment.path("gross").asText()+" "+currency+(en?"; work ":"; Arbeit ")+from+"–"+through+(en?"; entitlement ":"; Anspruch ")+claim+(en?"; received ":"; Zufluss ")+received,normalFont));
                    }
                }
                var assessment=remuneration.path("assessment");
                if(assessment.isObject()&&assessment.path("originalPeriods").isArray()&&!assessment.path("originalPeriods").isEmpty()) {
                    doc.add(new Paragraph(en?"Settlement of original periods":"Differenzausgleich für Altperioden",labelFont));
                    doc.add(new Paragraph(en?"Original payroll figures remain unchanged. Only the additional amounts in the deduction/contribution tables are settled in this document.":"Die Originalwerte bleiben unverändert. Nur die zusätzlichen Beträge der Abzugs-/Beitragstabellen werden mit diesem Beleg verrechnet.",normalFont));
                    for(int n=0;n<assessment.path("originalPeriods").size();n++) {
                        var original=assessment.path("originalPeriods").get(n);var revised=assessment.path("revisedPeriods").path(n);
                        String date=snapshotJson.treeToValue(original.path("periodStart"),java.time.LocalDate.class).toString();
                        var taxBase=revised.path("remuneration").path("taxGross").path("total");var socialBase=revised.path("remuneration").path("socialGross").path("total");
                        boolean charged=java.util.stream.Stream.of(assessment.path("employee"),assessment.path("employer")).anyMatch(map->{var names=map.fieldNames();while(names.hasNext())if(names.next().contains(" · Altperiode "+date))return true;return false;});
                        if(!charged&&taxBase.decimalValue().compareTo(original.path("gross").path("total").decimalValue())==0&&socialBase.decimalValue().compareTo(original.path("gross").path("total").decimalValue())==0)continue;
                        PdfPTable historyTable=new PdfPTable(new float[]{3,2,2});historyTable.setWidthPercentage(100);historyTable.setSpacingBefore(6);historyTable.setSpacingAfter(6);historyTable.setKeepTogether(true);
                        historyTable.addCell(headerCell(date));historyTable.addCell(headerCell(en?"Original":"Original"));historyTable.addCell(headerCell(en?"Revised basis":"Neue Grundlage"));
                        java.util.List<String[]> rows=new java.util.ArrayList<>();
                        String total=original.path("gross").path("total").asText();rows.add(new String[]{en?"Withholding gross":"Steuerbrutto",total,taxBase.asText()});rows.add(new String[]{en?"Social-insurance gross":"SV-Brutto",total,socialBase.asText()});
                        if(original.path("germanBasis").isObject())for(String field:new String[]{"pensionBase","healthBase"})rows.add(new String[]{field.equals("pensionBase")?(en?"Pension / unemployment base":"RV-/ALV-Basis"):(en?"Health / care base":"KV-/PV-Basis"),original.path("germanBasis").path(field).asText(),revised.path("germanBasis").path(field).asText()});
                        for(var row:rows){historyTable.addCell(cell(row[0],normalFont,false));historyTable.addCell(cell(row[1]+" "+currency,normalFont,false));historyTable.addCell(cell(row[2]+" "+currency,normalFont,false));}doc.add(historyTable);
                    }
                    if(ps.getNetSalary()!=null&&ps.getNetSalary()<0)doc.add(new Paragraph(en?"Negative balance: repayment / offset to be clarified separately. This is not a bank payment order.":"Negativer Saldo: Rückforderung / Verrechnung gesondert klären. Dieser Beleg löst keine Bankzahlung aus.",normalFont));
                }
                var prior=saved.path("germanBasis").path("priorYear");
                if(prior.isObject())doc.add(new Paragraph((en?"March clause: social insurance allocated to ":"Märzklausel: SV-Zuordnung zum ")+snapshotJson.treeToValue(prior.path("assignmentDate"),java.time.LocalDate.class)+(en?"; pension base ":"; RV-/ALV-Basis ")+prior.path("pensionBase").asText()+" "+currency+(en?"; health base ":"; KV-/PV-Basis ")+prior.path("healthBase").asText()+" "+currency+"; "+prior.path("source").asText(),normalFont));
                var insurance=saved.path("swissBasis").path("insuranceSlices");
                if(insurance.isArray()&&!insurance.isEmpty()) {
                    doc.add(new Paragraph(en?"Insurance by coverage period":"Versicherung nach Deckungsabschnitt",labelFont));
                    PdfPTable split=new PdfPTable(new float[]{3,2,2,2});split.setWidthPercentage(100);split.setSpacingBefore(6);split.setSpacingAfter(6);split.setHeaderRows(1);
                    for(String columnTitle:new String[]{en?"Coverage":"Deckung",en?"UVG base":"UVG-Basis",en?"KTG base":"KTG-Basis","BVG AN / AG"})split.addCell(headerCell(columnTitle));
                    for(var row:insurance) {
                        split.addCell(cell(snapshotJson.treeToValue(row.path("from"),java.time.LocalDate.class)+"–"+snapshotJson.treeToValue(row.path("through"),java.time.LocalDate.class),normalFont,false));
                        split.addCell(cell(row.path("uvgBase").asText(),normalFont,false));split.addCell(cell(row.path("ktgBase").asText(),normalFont,false));split.addCell(cell(row.path("pensionEmployee").asText()+" / "+row.path("pensionEmployer").asText(),normalFont,false));
                    }
                    doc.add(split);
                }
                var allocations=snapshotJson.readTree(ps.getCalculationSnapshot()).path("swissBasis").path("withholdingAllocations");
                if(allocations.isArray()&&!allocations.isEmpty()) {
                    doc.add(new Paragraph(ps.isCorrection()?(en?"Withholding allocation of corrected monthly totals":"Quellensteuerzuordnung des korrigierten Monatsstands"):(en?"Withholding allocation by canton period":"Quellensteuer nach Kantonsabschnitt"),labelFont));
                    if(ps.isCorrection())doc.add(new Paragraph(en?"This allocation shows the recalculated monthly amounts. Only the differences in the deduction table below are charged or refunded.":"Diese Zuordnung zeigt die neu berechneten Monatsbeträge. Verrechnet werden nur die Differenzen in der nachfolgenden Abzugstabelle.",normalFont));
                    PdfPTable allocationTable=new PdfPTable(new float[]{1,2,2});allocationTable.setWidthPercentage(100);allocationTable.setSpacingBefore(6f);
                    allocationTable.addCell(headerCell(en?"Canton":"Kanton"));allocationTable.addCell(headerCell(en?"Period from":"Abschnitt ab"));allocationTable.addCell(headerCell(en?"Deduction / refund":"Abzug / Erstattung"));
                    for(var allocation:allocations) {
                        allocationTable.addCell(cell(allocation.path("canton").asText(),normalFont,false));
                        String from=snapshotJson.treeToValue(allocation.path("from"),java.time.LocalDate.class).toString();
                        allocationTable.addCell(cell(from,normalFont,false));
                        allocationTable.addCell(cell(String.format(java.util.Locale.ROOT,"%.2f %s",allocation.path("amount").asDouble(),currency),normalFont,false));
                    }
                    doc.add(allocationTable);
                }
            }

            doc.add(new Paragraph(" "));

            // ---- Verdienst (Earnings) ----
            PdfPTable earningsTable = new PdfPTable(new float[]{4, 2, 2});
            earningsTable.setWidthPercentage(70);
            earningsTable.setSpacingAfter(10);
            earningsTable.setKeepTogether(true);
            earningsTable.setHeaderRows(1);
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
                earningsTable.addCell(cell(currency, normalFont, false));

            }
            if(!ps.getEarnings().isEmpty())doc.add(earningsTable);

            // ---- Abzüge (Deductions) ----
            PdfPTable dedTable = new PdfPTable(new float[]{4, 2, 2});
            dedTable.setWidthPercentage(70);
            dedTable.setSpacingAfter(10);
            dedTable.setKeepTogether(true);
            dedTable.setHeaderRows(1);
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
                dedTable.addCell(cell(currency, normalFont, false));

            }
            doc.add(dedTable);
            doc.add(new Paragraph(" "));

            if (ps.getEmployerContribList() != null && !ps.getEmployerContribList().isEmpty()) {
                PdfPTable empTable = new PdfPTable(new float[]{4,2,2});
                empTable.setWidthPercentage(70);
                empTable.setSpacingAfter(10);
                empTable.setKeepTogether(true);
                empTable.setHeaderRows(1);
                empTable.addCell(headerCell(employerHeader));
                empTable.addCell(headerCell(amountHeader));
                empTable.addCell(headerCell(currencyHeader));
                for (var comp : ps.getEmployerContribList()) {
                    String etype = comp.getType();
                    if (!en && compTrans.containsKey(etype)) {
                        etype = compTrans.get(etype);
                    }
                    empTable.addCell(cell(etype, normalFont, false));
                    empTable.addCell(cell(String.format("%.2f", comp.getAmount()), normalFont, false));
                    empTable.addCell(cell(currency, normalFont, false));
                }
                if (ps.getEmployerContributions() != null) {
                    var bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                    empTable.addCell(cell(en ? "Total" : "Summe", bold, false));
                    empTable.addCell(cell(String.format("%.2f", ps.getEmployerContributions()), bold, false));
                    empTable.addCell(cell(currency, bold, false));
                }
                doc.add(empTable);
                doc.add(new Paragraph(" "));
            } else if (ps.getEmployerContributions() != null) {
                PdfPTable empTable = new PdfPTable(new float[]{4,2,2});
                empTable.setWidthPercentage(70);
                empTable.setSpacingAfter(10);
                empTable.setKeepTogether(true);
                empTable.setHeaderRows(1);
                empTable.addCell(headerCell(employerHeader));
                empTable.addCell(headerCell(amountHeader));
                empTable.addCell(headerCell(currencyHeader));
                empTable.addCell(cell(pensionLabel, normalFont, false));
                empTable.addCell(cell(String.format("%.2f", ps.getEmployerContributions()), normalFont, false));
                empTable.addCell(cell(currency, normalFont, false));
                doc.add(empTable);
                doc.add(new Paragraph(" "));
            }

            // ---- Überstunden- und Urlaubssaldo ----
            int overtimeMinutes = savedTerms == null && ps.getUser().getTrackingBalanceInMinutes() != null
                    ? ps.getUser().getTrackingBalanceInMinutes() : 0;
            double overtimeHours = overtimeMinutes / 60.0;
            // Today's balances must not be presented as historical payroll balances.
            double vacationDays = savedTerms != null ? 0 : vacationService.calculateRemainingVacationDays(
                    ps.getUser().getUsername(),
                    ps.getPeriodEnd() != null ? ps.getPeriodEnd().getYear() : LocalDate.now().getYear());

            boolean hasBilledOvertime = ps.isPayoutOvertime() && ps.getOvertimeHours() != null && ps.getOvertimeHours() != 0;
            if (overtimeMinutes != 0 || vacationDays > 0 || hasBilledOvertime) {
                PdfPTable saldoTable = new PdfPTable(2);
                saldoTable.setWidthPercentage(55);
                saldoTable.setSpacingAfter(8);
                String overtimeUnit = en ? "hrs" : "Std.";
                String vacationUnit = en ? "days" : "Tage";
                if (hasBilledOvertime) {
                    saldoTable.addCell(cell(billedOvertimeLabel, labelFont, true));
                    saldoTable.addCell(cell(String.format("%.2f %s", ps.getOvertimeHours(), overtimeUnit), normalFont, false));
                }
                if (overtimeMinutes != 0) {
                    saldoTable.addCell(cell(overtimeLabel, labelFont, true));
                    saldoTable.addCell(cell(String.format("%.1f %s", overtimeHours, overtimeUnit), normalFont, false));
                }
                if (vacationDays > 0) {
                    saldoTable.addCell(cell(vacationLabel, labelFont, true));
                    saldoTable.addCell(cell(String.format("%.1f %s", vacationDays, vacationUnit), normalFont, false));
                }
                doc.add(saldoTable);
            }


            // ---- Summenblock ----
            PdfPTable totals = new PdfPTable(3);
            totals.setWidthPercentage(70);
            totals.setKeepTogether(true);
            totals.setSpacingAfter(15);
            totals.addCell(headerCell(totalsHeader, BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell(amountHeader, BaseColor.LIGHT_GRAY));
            totals.addCell(headerCell(currencyHeader, BaseColor.LIGHT_GRAY));

            totals.addCell(cell(grossLabel, normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getGrossSalary()), normalFont, false));
            totals.addCell(cell(currency, normalFont, false));

            totals.addCell(cell(deductionsLabel, normalFont, false));
            totals.addCell(cell(String.format("%.2f", ps.getDeductions()), normalFont, false));
            totals.addCell(cell(currency, normalFont, false));

            totals.addCell(cell(netLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell(String.format("%.2f", ps.getNetSalary()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));
            totals.addCell(cell(currency, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), false));


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

            Paragraph contact = new Paragraph(contactText,
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY));
            contact.setAlignment(Element.ALIGN_LEFT);

            BarcodeQRCode barcode = new BarcodeQRCode("payslip-" + ps.getId(), 100, 100, null);
            Image qrImage = barcode.getImage();
            qrImage.scaleToFit(70, 70);
            PdfPTable signatures = new PdfPTable(new float[]{5, 1});
            signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(18);
            signatures.setKeepTogether(true);
            PdfPCell signatureCell = new PdfPCell(new Phrase("\n_______________________       _______________________\n" + signatureText,
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
            signatureCell.setBorder(Rectangle.NO_BORDER);
            signatures.addCell(signatureCell);
            PdfPCell qrCell = new PdfPCell(qrImage, false);
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            signatures.addCell(qrCell);
            PdfPTable footer=new PdfPTable(1);footer.setWidthPercentage(100);footer.setKeepTogether(true);
            PdfPCell footerCell=new PdfPCell();footerCell.setBorder(Rectangle.NO_BORDER);footerCell.setPadding(0);
            footerCell.addElement(legal);footerCell.addElement(contact);footerCell.addElement(signatures);
            footer.addCell(footerCell);doc.add(footer);

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
