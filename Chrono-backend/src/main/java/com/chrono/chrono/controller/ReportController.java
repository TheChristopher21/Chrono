package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.dto.ProjectReportDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ReportService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserService userService;

    private boolean featureEnabled(User user) {
        return user != null && user.getCompany() != null && Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled());
    }

    @GetMapping("/analytics/projects")
    public ResponseEntity<List<ProjectHierarchyNodeDTO>> projectAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByUsername(principal.getName());
        if (!featureEnabled(user)) {
            return ResponseEntity.status(403).build();
        }
        LocalDate start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate) : null;
        List<ProjectHierarchyNodeDTO> analytics = reportService.getProjectAnalytics(user.getCompany().getId(), start, end);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ProjectReportDTO> projectReport(
            @PathVariable Long projectId,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        ProjectReportDTO dto = reportService.generateProjectReport(
                projectId,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate)
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/timesheet/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        try {
            byte[] pdfBytes = reportService.generatePdf(
                    username,
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("timesheet.pdf").build());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/timesheet/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        byte[] csv = reportService.generateCsv(
                username,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(ContentDisposition.attachment().filename("timesheet.csv").build());

        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @GetMapping("/timesheet/ics")
    public ResponseEntity<byte[]> downloadIcs(
            @RequestParam String username,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String zone
    ) {
        ZoneId zoneId = zone != null ? ZoneId.of(zone) : ZoneId.systemDefault();
        byte[] ics = reportService.generateIcs(
                username,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                zoneId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("timesheet.ics").build());

        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }

    @GetMapping("/timesheet/ics-feed/{username}")
    public ResponseEntity<byte[]> icsFeed(
            @PathVariable String username,
            @RequestParam(required = false) String zone
    ) {
        ZoneId zoneId = zone != null ? ZoneId.of(zone) : ZoneId.systemDefault();
        byte[] ics = reportService.generateIcsFeed(username, zoneId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }
}
