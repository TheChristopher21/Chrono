package com.chrono.chrono.controller;

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

    // PDF Download
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

    // CSV Download
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
}
