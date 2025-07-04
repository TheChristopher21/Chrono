package com.chrono.chrono.controller;

import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/calendar")
public class CalendarIntegrationController {

    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/{username}.ics")
    public ResponseEntity<byte[]> exportCalendar(@PathVariable String username,
                                                 @RequestParam String start,
                                                 @RequestParam String end) {
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        String ics = timeTrackingService.generateIcs(username, startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(username + ".ics").build());
        return ResponseEntity.ok().headers(headers).body(ics.getBytes(StandardCharsets.UTF_8));
    }
}
