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
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Erzeugt ein PDF mit den TimeTracking-Einträgen (1 pro Tag) im Zeitraum [start, end].
     * Für stundenbasierte User summieren wir die gearbeiteten Minuten (ohne Pausensubtraktion?)
     * oder "mit" – je nachdem, wie du es willst.
     */
    public byte[] generatePdf(String username, LocalDate start, LocalDate end) throws IOException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        // Hier holen wir am besten direkt getReport(...) oder getUserHistory(...) =>
        //   getReport(username, start.toString(), end.toString())
        //   oder wir filtern manuell:
        List<TimeTrackingResponse> all = timeTrackingService.getUserHistory(username);

        // Filter nach dailyDate
        List<TimeTrackingResponse> filtered = all.stream()
                .filter(tt -> {
                    LocalDate d = tt.getDailyDate();
                    return !d.isBefore(start) && !d.isAfter(end);
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

        // Falls stundenbasierter Mitarbeiter: Gesamtminuten summieren
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            long totalMinutes = 0;
            for (TimeTrackingResponse r : filtered) {
                totalMinutes += computeWorkedMinutes(r);
            }
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            doc.add(new Paragraph("Summe gearbeitete Zeit (stundenbasiert): "
                    + hours + " Std " + minutes + " min"));
            doc.add(new Paragraph(" "));
        }

        // Einzelne Tage auflisten
        for (TimeTrackingResponse r : filtered) {
            LocalDate d = r.getDailyDate();
            String line = String.format("Tag: %s | Work: %s-%s | Break: %s-%s | Note: %s",
                    d,
                    (r.getWorkStart() != null ? r.getWorkStart() : "--:--"),
                    (r.getWorkEnd()   != null ? r.getWorkEnd()   : "--:--"),
                    (r.getBreakStart() != null ? r.getBreakStart() : "--:--"),
                    (r.getBreakEnd()   != null ? r.getBreakEnd()   : "--:--"),
                    (r.getDailyNote() != null ? r.getDailyNote() : "")
            );
            doc.add(new Paragraph(line));
        }

        doc.close();
        return baos.toByteArray();
    }

    /**
     * Erzeugt einen CSV-Report im Zeitraum [start, end].
     */
    public byte[] generateCsv(String username, LocalDate start, LocalDate end) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        // Hole alle Einträge
        List<TimeTrackingResponse> all = timeTrackingService.getUserHistory(username);
        List<TimeTrackingResponse> filtered = all.stream()
                .filter(tt -> {
                    LocalDate d = tt.getDailyDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .collect(Collectors.toList());

        // CSV-Header
        StringBuilder sb = new StringBuilder();
        sb.append("User;").append(user.getUsername()).append("\n");
        sb.append("Name;").append(user.getFirstName())
                .append(" ").append(user.getLastName()).append("\n");
        sb.append("Date;WorkStart;BreakStart;BreakEnd;WorkEnd;DailyNote\n");

        // Zeilen
        for (TimeTrackingResponse r : filtered) {
            sb.append(r.getDailyDate()).append(";");
            sb.append(r.getWorkStart() == null ? "" : r.getWorkStart()).append(";");
            sb.append(r.getBreakStart() == null ? "" : r.getBreakStart()).append(";");
            sb.append(r.getBreakEnd() == null ? "" : r.getBreakEnd()).append(";");
            sb.append(r.getWorkEnd() == null ? "" : r.getWorkEnd()).append(";");
            sb.append(r.getDailyNote() == null ? "" : r.getDailyNote()).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * worked = (workEnd - workStart) minus (breakEnd - breakStart).
     * mind. 0
     */
    private long computeWorkedMinutes(TimeTrackingResponse r) {
        if (r.getWorkStart() == null || r.getWorkEnd() == null) {
            return 0L;
        }
        long total = ChronoUnit.MINUTES.between(r.getWorkStart(), r.getWorkEnd());
        if (r.getBreakStart() != null && r.getBreakEnd() != null) {
            total -= ChronoUnit.MINUTES.between(r.getBreakStart(), r.getBreakEnd());
        }
        return Math.max(total, 0L);
    }
}
