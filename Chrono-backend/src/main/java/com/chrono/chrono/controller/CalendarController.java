package com.chrono.chrono.controller;

import com.chrono.chrono.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/ics")
    public ResponseEntity<String> getIcs(@RequestParam String username,
                                         @RequestParam String startDate,
                                         @RequestParam String endDate) {
        String ics = reportService.generateIcs(username, LocalDate.parse(startDate), LocalDate.parse(endDate));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chrono.ics");
        return ResponseEntity.ok().headers(headers).body(ics);
    }
}
