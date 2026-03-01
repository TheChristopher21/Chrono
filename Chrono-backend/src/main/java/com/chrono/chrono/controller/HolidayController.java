package com.chrono.chrono.controller;

import com.chrono.chrono.services.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    /**
     * Endpunkt zum Abrufen von Feiertagsdetails für ein bestimmtes Jahr, einen Kanton
     * und einen Datumsbereich.
     *
     * Beispielaufruf: GET /api/holidays/details?year=2024&cantonAbbreviation=SG&startDate=2024-01-01&endDate=2024-12-31
     *
     * @param year Das Jahr, für das die Feiertage abgerufen werden sollen.
     * @param cantonAbbreviation Optional. Das zweistellige Kürzel des Kantons (z.B. "SG", "ZH").
     * Wenn nicht angegeben, werden allgemeine/nationale Feiertage verwendet.
     * @param startDate Das Startdatum des Zeitraums (Format JJJJ-MM-TT).
     * @param endDate Das Enddatum des Zeitraums (Format JJJJ-MM-TT).
     * @return Eine Map, die Feiertagsdaten auf ihre Namen abbildet.
     */
    @GetMapping("/details")
    public ResponseEntity<Map<LocalDate, String>> getHolidayDetails(
            @RequestParam int year,
            @RequestParam(required = false) String cantonAbbreviation,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Validierung, ob der Zeitraum mit dem Jahr übereinstimmt (optional, aber sinnvoll)
        // In diesem Fall wird die Methode im HolidayService verwendet, die auch jahresübergreifend funktioniert,
        // solange startDate und endDate den relevanten Bereich abdecken. Das 'year'-Param hier dient primär
        // der initialen Auswahl im HolidayService-Caching, falls nicht jahresübergreifend.
        // Für eine strikte Jahresbindung könnte man hier prüfen:
        // if (startDate.getYear() != year || endDate.getYear() != year) {
        //     return ResponseEntity.badRequest().body(Map.of()); // oder eine Fehlermeldung
        // }

        Map<LocalDate, String> holidayDetails = holidayService.getHolidayDetails(year, cantonAbbreviation, startDate, endDate);
        return ResponseEntity.ok(holidayDetails);
    }

    /**
     * Alternativer Endpunkt, der nur eine Liste von Feiertagsdaten für ein Jahr und einen Kanton zurückgibt.
     *
     * Beispielaufruf: GET /api/holidays?year=2024&cantonAbbreviation=SG
     *
     * @param year Das Jahr.
     * @param cantonAbbreviation Optional. Das Kantonskürzel.
     * @return Ein Set von LocalDate-Objekten, die die Feiertage darstellen.
     */
    @GetMapping
    public ResponseEntity<Set<LocalDate>> getHolidaysRaw(
            @RequestParam int year,
            @RequestParam(required = false) String cantonAbbreviation) {
        Set<LocalDate> holidays = holidayService.getHolidays(year, cantonAbbreviation);
        return ResponseEntity.ok(holidays);
    }

    /**
     * Endpunkt, um zu prüfen, ob ein bestimmtes Datum ein Feiertag ist.
     *
     * Beispielaufruf: GET /api/holidays/is-holiday?date=2024-08-01&cantonAbbreviation=SG
     *
     * @param date Das zu prüfende Datum (Format JJJJ-MM-TT).
     * @param cantonAbbreviation Optional. Das Kantonskürzel.
     * @return true, wenn es ein Feiertag ist, sonst false.
     */
    @GetMapping("/is-holiday")
    public ResponseEntity<Boolean> isHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String cantonAbbreviation) {
        boolean isHoliday = holidayService.isHoliday(date, cantonAbbreviation);
        return ResponseEntity.ok(isHoliday);
    }
}