package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ProjectReportDTO;
import com.chrono.chrono.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

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
            @RequestParam String endDate
    ) {
        byte[] ics = reportService.generateIcs(
                username,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("timesheet.ics").build());

        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }

    @GetMapping("/timesheet/ics-feed/{username}")
    public ResponseEntity<byte[]> icsFeed(@PathVariable String username) {
        byte[] ics = reportService.generateIcsFeed(username);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }
}
