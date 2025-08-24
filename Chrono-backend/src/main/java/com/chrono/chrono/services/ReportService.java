package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.TimeTrackingEntryDTO;
import com.chrono.chrono.dto.ProjectReportDTO;
import com.chrono.chrono.dto.TaskReportDTO;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    private static final DateTimeFormatter DATE_FORMATTER_REPORT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN);
    private static final DateTimeFormatter TIME_FORMATTER_REPORT = DateTimeFormatter.ofPattern("HH:mm");

    public ProjectReportDTO generateProjectReport(Long projectId, LocalDate start, LocalDate end) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();
        List<TimeTrackingEntry> entries = timeTrackingEntryRepository
                .findByProjectAndEntryTimestampBetween(project, startDt, endDt);

        Map<Task, Long> totals = new LinkedHashMap<>();
        long totalMinutes = 0;
        for (TimeTrackingEntry e : entries) {
            int mins = e.getDurationMinutes() != null ? e.getDurationMinutes() : 0;
            totalMinutes += mins;
            Task task = e.getTask();
            totals.merge(task, (long) mins, Long::sum);
        }

        List<TaskReportDTO> taskDtos = totals.entrySet().stream()
                .map(en -> {
                    Task t = en.getKey();
                    Long mins = en.getValue();
                    Long taskId = t != null ? t.getId() : null;
                    String taskName = t != null ? t.getName() : "Unassigned";
                    Integer budget = t != null ? t.getBudgetMinutes() : null;
                    return new TaskReportDTO(taskId, taskName, mins, budget);
                })
                .collect(Collectors.toList());

        return new ProjectReportDTO(project.getId(), project.getName(), totalMinutes,
                project.getBudgetMinutes(), taskDtos);
    }

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
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
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

        Color headerBg = new Color(71, 91, 255);

        PdfPCell cell = new PdfPCell(new Paragraph("Datum", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Arbeitszeit", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Pause", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Stempelungen (START/ENDE)", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
        cell = new PdfPCell(new Paragraph("Notiz / Status", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);

        long totalWorkedMinutesOverall = 0;
        boolean odd = false;

        for (DailyTimeSummaryDTO summary : dailySummaries) {
            odd = !odd;
            Color rowColor = odd ? new Color(245, 245, 245) : Color.WHITE;

            PdfPCell c1 = new PdfPCell(new Paragraph(summary.getDate().format(DATE_FORMATTER_REPORT), regularFont));
            c1.setBackgroundColor(rowColor);
            c1.setPadding(4);
            table.addCell(c1);

            long hours = summary.getWorkedMinutes() / 60;
            long minutes = summary.getWorkedMinutes() % 60;
            PdfPCell c2 = new PdfPCell(new Paragraph(String.format("%02d:%02d Std.", hours, minutes), regularFont));
            c2.setBackgroundColor(rowColor);
            c2.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            c2.setPadding(4);
            table.addCell(c2);
            totalWorkedMinutesOverall += summary.getWorkedMinutes();

            long breakH = summary.getBreakMinutes() / 60;
            long breakM = summary.getBreakMinutes() % 60;
            PdfPCell c3 = new PdfPCell(new Paragraph(String.format("%02d:%02d Std.", breakH, breakM), regularFont));
            c3.setBackgroundColor(rowColor);
            c3.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            c3.setPadding(4);
            table.addCell(c3);

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
            PdfPCell c4 = new PdfPCell(new Paragraph(stamps.toString().trim(), smallFont));
            c4.setBackgroundColor(rowColor);
            c4.setPadding(4);
            table.addCell(c4);

            StringBuilder noteCell = new StringBuilder();
            if (summary.getDailyNote() != null && !summary.getDailyNote().isBlank()) {
                 noteCell.append(summary.getDailyNote()).append("\n");
            }
            if (summary.isNeedsCorrection()) {
                noteCell.append("[Korrektur erforderlich!]");
            }
            PdfPCell c5 = new PdfPCell(new Paragraph(noteCell.toString().trim(), regularFont));
            c5.setBackgroundColor(rowColor);
            c5.setPadding(4);
            table.addCell(c5);
        }
        document.add(table);
        document.add(new Paragraph(" "));

        long totalHours = totalWorkedMinutesOverall / 60;
        long totalMinutes = totalWorkedMinutesOverall % 60;
        document.add(new Paragraph("Gesamte Arbeitszeit im Zeitraum: " + String.format("%d Std. %02d Min.", totalHours, totalMinutes), headerFont));

        int overtimeMinutes = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0;
        long overtimeHours = overtimeMinutes / 60;
        long overtimeRemainder = overtimeMinutes % 60;
        document.add(new Paragraph("Aktueller Überstundensaldo: " + String.format("%d Std. %02d Min.", overtimeHours, overtimeRemainder), headerFont));

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

    public byte[] generateIcs(String username, LocalDate start, LocalDate end, ZoneId zoneId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        List<TimeTrackingEntry> entries = timeTrackingService.getEntriesForUser(user,
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        DateTimeFormatter fmtUtc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
        DateTimeFormatter fmtLocal = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
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
            sb.append("DTSTAMP:").append(fmtUtc.format(Instant.now())).append("\r\n");

            ZonedDateTime startZ = startEntry.getEntryTimestamp().atZone(zoneId);
            ZonedDateTime endZ = endEntry.getEntryTimestamp().atZone(zoneId);
            if (ZoneOffset.UTC.equals(zoneId)) {
                sb.append("DTSTART:").append(fmtUtc.format(startZ)).append("\r\n");
                sb.append("DTEND:").append(fmtUtc.format(endZ)).append("\r\n");
            } else {
                sb.append("DTSTART;TZID=").append(zoneId.getId()).append(":")
                        .append(fmtLocal.format(startZ)).append("\r\n");
                sb.append("DTEND;TZID=").append(zoneId.getId()).append(":")
                        .append(fmtLocal.format(endZ)).append("\r\n");
            }
            sb.append("SUMMARY:Work\r\n");
            sb.append("END:VEVENT\r\n");
            i++; // skip paired end entry
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generateIcs(String username, LocalDate start, LocalDate end) {
        return generateIcs(username, start, end, ZoneId.systemDefault());
    }

    /**
     * Convenience wrapper to generate an iCalendar feed for a user without
     * specifying a date range. The feed covers a wide time span so that new
     * entries will appear automatically when subscribed from external calendar
     * applications.
     */
    public byte[] generateIcsFeed(String username, ZoneId zoneId) {
        LocalDate start = LocalDate.now().minusYears(1);
        LocalDate end = LocalDate.now().plusYears(1);
        return generateIcs(username, start, end, zoneId);
    }

    public byte[] generateIcsFeed(String username) {
        return generateIcsFeed(username, ZoneId.systemDefault());
    }
}
