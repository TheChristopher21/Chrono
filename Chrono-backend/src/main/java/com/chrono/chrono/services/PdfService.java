package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import org.springframework.stereotype.Service;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class PdfService {
    public String generatePayslipPdf(Payslip ps) {
        String path = "/tmp/payslip-" + ps.getId() + ".pdf";
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(path));
            doc.open();
            doc.add(new Paragraph("Payslip " + ps.getId()));
            doc.add(new Paragraph("Period: " + ps.getPeriodStart() + " - " + ps.getPeriodEnd()));
            doc.add(new Paragraph("Net salary: " + ps.getNetSalary()));
        } catch (DocumentException | IOException e) {
            return null;
        } finally {
            doc.close();
        }
        return path;
    }
}
