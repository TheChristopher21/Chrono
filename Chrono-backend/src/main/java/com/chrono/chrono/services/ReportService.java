package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER_REPORT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN);
    private static final DateTimeFormatter TIME_FORMATTER_REPORT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] generatePdf(String username, LocalDate start, LocalDate end) throws IOException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        List<DailyTimeSummaryDTO> dailySummaries = timeTrackingService.getUserHistory(username).stream()
                .filter(summary -> !summary.getDate().isBefore(start) && !summary.getDate().isAfter(end))
                .sorted(java.util.Comparator.comparing(DailyTimeSummaryDTO::getDate))
                .collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font regularFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font smallFont = new Font(Font.HELVETICA, 8, Font.ITALIC);

        document.add(new Paragraph("Zeitnachweis für: " + user.getFirstName() + " " + user.getLastName() + " (" + user.getUsername() + ")", titleFont));
        document.add(new Paragraph("Zeitraum: " + start.format(DATE_FORMATTER_REPORT) + " - " + end.format(DATE_FORMATTER_REPORT), regularFont));
        document.add(new Paragraph(" "));

        if (dailySummaries.isEmpty()) {
            document.add(new Paragraph("Keine Einträge für den ausgewählten Zeitraum vorhanden.", regularFont));
            document.close();
            return baos.toByteArray();
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.5f, 1.5f, 1f, 4f, 3f});

        PdfPCell cell = new PdfPCell(new Paragraph("Datum", headerFont));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Arbeitszeit", headerFont));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Pause", headerFont));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Stempelungen (START/ENDE)", headerFont));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Notiz / Status", headerFont));
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(cell);

        long totalWorkedMinutesOverall = 0;

        for (DailyTimeSummaryDTO summary : dailySummaries) {
            table.addCell(new Paragraph(summary.getDate().format(DATE_FORMATTER_REPORT), regularFont));
            
            long hours = summary.getWorkedMinutes() / 60;
            long minutes = summary.getWorkedMinutes() % 60;
            table.addCell(new Paragraph(String.format("%02d:%02d Std.", hours, minutes), regularFont));
            totalWorkedMinutesOverall += summary.getWorkedMinutes();

            long breakH = summary.getBreakMinutes() / 60;
            long breakM = summary.getBreakMinutes() % 60;
            table.addCell(new Paragraph(String.format("%02d:%02d Std.", breakH, breakM), regularFont));

            StringBuilder stamps = new StringBuilder();
            if (summary.getEntries() != null) {
                for (TimeTrackingEntryDTO entry : summary.getEntries()) {
                    stamps.append(entry.getPunchType().toString())
                          .append(": ")
                          .append(entry.getEntryTimestamp().format(TIME_FORMATTER_REPORT));
                    if (entry.getSource() == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END && !entry.isCorrectedByUser()) {
                        stamps.append(" (Auto)");
                    }
                    stamps.append("\n");
                }
            }
            table.addCell(new Paragraph(stamps.toString().trim(), smallFont));

            StringBuilder noteCell = new StringBuilder();
            if (summary.getDailyNote() != null && !summary.getDailyNote().isBlank()) {
                 noteCell.append(summary.getDailyNote()).append("\n");
            }
            if (summary.isNeedsCorrection()) {
                noteCell.append("[Korrektur erforderlich!]");
            }
            table.addCell(new Paragraph(noteCell.toString().trim(), regularFont));
        }
        document.add(table);
        document.add(new Paragraph(" "));

        long totalHours = totalWorkedMinutesOverall / 60;
        long totalMinutes = totalWorkedMinutesOverall % 60;
        document.add(new Paragraph("Gesamte Arbeitszeit im Zeitraum: " + String.format("%d Std. %02d Min.", totalHours, totalMinutes), headerFont));

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateCsv(String username, LocalDate start, LocalDate end) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        List<DailyTimeSummaryDTO> dailySummaries = timeTrackingService.getUserHistory(username).stream()
                .filter(summary -> !summary.getDate().isBefore(start) && !summary.getDate().isAfter(end))
                .sorted(java.util.Comparator.comparing(DailyTimeSummaryDTO::getDate))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Benutzer;").append(user.getFirstName()).append(" ").append(user.getLastName()).append(" (").append(user.getUsername()).append(")\n");
        sb.append("Zeitraum;").append(start.format(DATE_FORMATTER_REPORT)).append(" - ").append(end.format(DATE_FORMATTER_REPORT)).append("\n\n");
        sb.append("Datum;Arbeitszeit (Min);Pausenzeit (Min);Stempelungen (Typ:Zeit);Notiz;Korrektur Nötig\n");

        long totalWorkedMinutesOverall = 0;

        for (DailyTimeSummaryDTO summary : dailySummaries) {
            sb.append(summary.getDate().format(DATE_FORMATTER_REPORT)).append(";");
            sb.append(summary.getWorkedMinutes()).append(";");
            sb.append(summary.getBreakMinutes()).append(";");
            totalWorkedMinutesOverall += summary.getWorkedMinutes();

            String stamps = summary.getEntries().stream()
                .map(e -> e.getPunchType().toString() + ":" + e.getEntryTimestamp().format(TIME_FORMATTER_REPORT) + 
                           (e.getSource() == TimeTrackingEntry.PunchSource.SYSTEM_AUTO_END && !e.isCorrectedByUser() ? "(Auto)" : ""))
                .collect(Collectors.joining(" | "));
            sb.append("\"").append(stamps).append("\"").append(";");

            sb.append(summary.getDailyNote() != null ? "\"" + summary.getDailyNote().replace("\"", "\"\"") + "\"" : "").append(";");
            sb.append(summary.isNeedsCorrection() ? "JA" : "NEIN").append("\n");
        }
        
        sb.append("\nGesamte Arbeitszeit (Min);").append(totalWorkedMinutesOverall).append("\n");
        long totalHours = totalWorkedMinutesOverall / 60;
        long totalMinutes = totalWorkedMinutesOverall % 60;
        sb.append("Gesamte Arbeitszeit (Formatiert);").append(String.format("%d Std. %02d Min.", totalHours, totalMinutes)).append("\n");


        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generateIcs(String username, LocalDate start, LocalDate end) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        List<TimeTrackingEntry> entries = timeTrackingService.getEntriesForUser(user,
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Chrono//Time Tracking//EN\r\n");

        for (int i = 0; i < entries.size(); i++) {
            TimeTrackingEntry startEntry = entries.get(i);
            if (startEntry.getPunchType() != TimeTrackingEntry.PunchType.START) continue;
            if (i + 1 >= entries.size()) break;
            TimeTrackingEntry endEntry = entries.get(i + 1);
            if (endEntry.getPunchType() != TimeTrackingEntry.PunchType.ENDE) continue;

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(startEntry.getId()).append("-").append(endEntry.getId()).append("@chrono\r\n");
            sb.append("DTSTAMP:").append(fmt.format(Instant.now())).append("\r\n");
            sb.append("DTSTART:").append(fmt.format(startEntry.getEntryTimestamp().toInstant(ZoneOffset.UTC))).append("\r\n");
            sb.append("DTEND:").append(fmt.format(endEntry.getEntryTimestamp().toInstant(ZoneOffset.UTC))).append("\r\n");
            sb.append("SUMMARY:Work\r\n");
            sb.append("END:VEVENT\r\n");
            i++; // skip paired end entry
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
