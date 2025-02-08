package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private TimeTrackingService timeTrackingService; // your existing 4-steps logic
    @Autowired
    private UserRepository userRepository;

    public byte[] generatePdf(String username, LocalDate start, LocalDate end) throws IOException {
        // check user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        List<TimeTrackingResponse> all = timeTrackingService.getUserHistory(username);

        // Filtern auf Zeitraum
        List<TimeTrackingResponse> filtered = all.stream()
                .filter(t -> {
                    LocalDate d = t.getStartTime().toLocalDate();
                    return !(d.isBefore(start) || d.isAfter(end));
                })
                .collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        doc.add(new Paragraph("Time Report for user: " + user.getUsername()));
        doc.add(new Paragraph("Name: " + user.getFirstName() + " " + user.getLastName()));
        doc.add(new Paragraph("Zeitraum: " + start + " - " + end));
        doc.add(new Paragraph(" "));

        for (TimeTrackingResponse r : filtered) {
            String line = String.format("%s | Start: %s | End: %s",
                    r.getColor(),
                    r.getStartTime(),
                    r.getEndTime() == null ? "Ongoing" : r.getEndTime());
            doc.add(new Paragraph(line));
        }

        doc.close();
        return baos.toByteArray();
    }

    public byte[] generateCsv(String username, LocalDate start, LocalDate end) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        List<TimeTrackingResponse> all = timeTrackingService.getUserHistory(username);
        List<TimeTrackingResponse> filtered = all.stream()
                .filter(t -> {
                    LocalDate d = t.getStartTime().toLocalDate();
                    return !(d.isBefore(start) || d.isAfter(end));
                })
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("User;").append(user.getUsername()).append("\n");
        sb.append("Name;").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        sb.append("Start;End;Status\n");

        for (TimeTrackingResponse r : filtered) {
            sb.append(r.getStartTime()).append(";");
            sb.append(r.getEndTime() == null ? "" : r.getEndTime()).append(";");
            sb.append(r.getColor()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
