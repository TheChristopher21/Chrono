// Datei: Chrono-backend/src/main/java/com/chrono/chrono/services/HolidayService.java
package com.chrono.chrono.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    private static final Logger logger = LoggerFactory.getLogger(HolidayService.class);

    // Cache für Feiertage pro Jahr und Kanton, um Neuberechnungen zu vermeiden
    private final Map<Integer, Map<String, Set<LocalDate>>> holidayCache = new HashMap<>();
    // Cache für rein nationale Feiertage (unabhängig von "weit verbreitet")
    private final Map<Integer, Set<LocalDate>> nationalHolidayCache = new HashMap<>();

    /**
     * Gibt alle relevanten Feiertage für ein Jahr und einen Kanton zurück.
     * Enthält nationale, weit verbreitete und kantonsspezifische Feiertage.
     * @param year Jahr
     * @param cantonAbbreviation Zweistelliges Kantonskürzel (z.B. "SG", "ZH").
     * Wenn null oder leer, werden nur nationale und sehr weit verbreitete Feiertage berücksichtigt.
     * @return Set von Feiertagsdaten
     */
    public Set<LocalDate> getHolidays(int year, String cantonAbbreviation) {
        String cantonKey = (cantonAbbreviation == null || cantonAbbreviation.trim().isEmpty()) ? "GENERAL" : cantonAbbreviation.toUpperCase();

        return holidayCache
                .computeIfAbsent(year, k -> new HashMap<>())
                .computeIfAbsent(cantonKey, k -> calculateHolidaysForCache(year, cantonAbbreviation));
    }

    /**
     * Gibt die rein nationalen Feiertage der Schweiz zurück.
     * @param year Jahr
     * @return Set von Feiertagsdaten
     */
    public Set<LocalDate> getNationalHolidays(int year) {
        return nationalHolidayCache.computeIfAbsent(year, this::calculateStrictNationalHolidays);
    }

    /**
     * Berechnet die Feiertage für den Cache.
     * Diese Methode wird intern vom Caching-Mechanismus aufgerufen.
     */
    private Set<LocalDate> calculateHolidaysForCache(int year, String cantonAbbreviation) {
        Set<LocalDate> holidays = new HashSet<>();
        LocalDate easterSunday = calculateEasterSunday(year);

        // 1. Nationale Feiertage (Bundesfeiertag + dem Bundesfeiertag gleichgestellte Tage)
        // Quelle: Art. 1 Abs. 1 ArG und Praxis (admin.ch)
        holidays.add(LocalDate.of(year, 1, 1));   // Neujahr
        holidays.add(easterSunday.plusDays(39));  // Auffahrt (Christi Himmelfahrt)
        holidays.add(LocalDate.of(year, 8, 1));   // Nationalfeiertag (Bundesfeiertag)
        holidays.add(LocalDate.of(year, 12, 25)); // Weihnachtstag

        // 2. Weit verbreitete Feiertage (in vielen/allen Kantonen arbeitsfrei und Geschäfte oft geschlossen)
        holidays.add(easterSunday.minusDays(2));  // Karfreitag
        holidays.add(easterSunday.plusDays(1));   // Ostermontag
        holidays.add(easterSunday.plusDays(50));  // Pfingstmontag
        holidays.add(LocalDate.of(year, 12, 26)); // Stephanstag / Zweiter Weihnachtstag

        // Tag der Arbeit - variiert stark, aber für "Geschäfte geschlossen" in vielen urbanen Gebieten relevant
        // Hier als "allgemein" drin, kann aber kantonal präzisiert werden.
        // In einigen Kantonen ganz frei, in anderen halbtags oder gar nicht.
        // Für das Kriterium "Geschäfte geschlossen" ist es oft ein Indikator.
        holidays.add(LocalDate.of(year, 5, 1));   // Tag der Arbeit

        // 3. Kantonale Feiertage (Beispiele, erweiterbar)
        // Wichtig: Die Logik hier sollte auf verlässlichen Quellen für kantonale Feiertage basieren.
        if (cantonAbbreviation != null && !cantonAbbreviation.trim().isEmpty()) {
            String cantonUpper = cantonAbbreviation.toUpperCase();
            switch (cantonUpper) {
                case "SG": // St. Gallen
                    holidays.add(LocalDate.of(year, 1, 2));  // Berchtoldstag
                    holidays.add(LocalDate.of(year, 11, 1)); // Allerheiligen
                    // Weitere spezifische Feiertage für SG, falls bekannt und relevant für "Geschäfte geschlossen"
                    break;
                case "ZH": // Zürich
                    holidays.add(LocalDate.of(year, 1, 2));  // Berchtoldstag
                    // Sechseläuten (Montagnachmittag) und Knabenschiessen (Montag) sind typischerweise halbe Feiertage,
                    // an denen Geschäfte oft früher schliessen oder ganz zu sind. Für "ganztägig geschlossen" ggf. nicht relevant.
                    // Ggf. spezifische Logik für halbe Feiertage einführen, falls das Wochensoll beeinflusst wird.
                    holidays.add(LocalDate.of(year, 11, 1)); // Allerheiligen (in einigen Gemeinden) - prüfen!
                    break;
                // Fügen Sie hier weitere Kantone nach Bedarf hinzu:
                // AG, AI, AR, BE, BL, BS, FR, GE, GL, GR, JU, LU, NE, NW, OW, SH, SO, SZ, TG, TI, UR, VD, VS, ZG
                // Beispiel für einen weiteren Kanton:
                // case "BE": // Bern
                //     holidays.add(LocalDate.of(year, 1, 2));  // Berchtoldstag
                //     holidays.add(LocalDate.of(year, 12, 26)); // Stephanstag ist in BE ein hoher Feiertag
                //     break;
                default:
                    // Für nicht explizit definierte Kantone werden nur die nationalen und oben genannten
                    // weit verbreiteten Feiertage verwendet.
                    logger.debug("Keine spezifischen kantonalen Feiertage für Kürzel '{}' im Jahr {} definiert. Verwende allgemeine Feiertage.", cantonUpper, year);
                    break;
            }
        }
        logger.info("Calculated holidays for year {} and canton '{}': {} days", year, cantonAbbreviation, holidays.size());
        return holidays;
    }

    /**
     * Berechnet die *strikt* nationalen Feiertage gemäss Bundesgesetz oder Gleichstellung.
     * (1. August ist der einzige eidgenössische. Neujahr, Auffahrt, Weihnachten sind kantonal, aber allen gleichgestellt)
     */
    private Set<LocalDate> calculateStrictNationalHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(year, 1, 1)); // Neujahr
        LocalDate easterSunday = calculateEasterSunday(year); // Wird für Auffahrt benötigt
        holidays.add(easterSunday.plusDays(39)); // Auffahrt
        holidays.add(LocalDate.of(year, 8, 1));  // Nationalfeiertag
        holidays.add(LocalDate.of(year, 12, 25)); // Weihnachten
        return holidays;
    }


    public boolean isHoliday(LocalDate date, String cantonAbbreviation) {
        if (date == null) {
            return false;
        }
        return getHolidays(date.getYear(), cantonAbbreviation).contains(date);
    }

    /**
     * Prüft, ob ein Datum ein allgemein anerkannter Feiertag ist (nationale + sehr weit verbreitete).
     * Nützlich, wenn kein spezifischer Kanton bekannt ist.
     */
    public boolean isGenerallyConsideredHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        // Verwendet die Logik von getHolidays ohne spezifisches Kantonskürzel
        return getHolidays(date.getYear(), null).contains(date);
    }


    private LocalDate calculateEasterSunday(int year) {
        // Gauss-Osterformel (Meeus/Jones/Butcher Algorithmus)
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /**
     * Gibt eine Liste von Feiertagsnamen und -daten für einen bestimmten Zeitraum und Kanton zurück.
     * Nützlich für die Anzeige im Frontend.
     */
    public Map<LocalDate, String> getHolidayDetails(int year, String cantonAbbreviation, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, String> holidayDetails = new HashMap<>();

        // Behandle jahresübergreifende Zeiträume
        Set<Integer> yearsToConsider = new HashSet<>();
        yearsToConsider.add(startDate.getYear());
        yearsToConsider.add(endDate.getYear());

        for (Integer currentYear : yearsToConsider) {
            Set<LocalDate> holidaysInYear = getHolidays(currentYear, cantonAbbreviation);
            holidaysInYear.stream()
                    .filter(holiday -> !holiday.isBefore(startDate) && !holiday.isAfter(endDate))
                    .sorted() // Sortieren für konsistente Reihenfolge, falls relevant
                    .forEach(holiday -> holidayDetails.put(holiday, getHolidayName(holiday, currentYear, cantonAbbreviation)));
        }
        return holidayDetails;
    }

    // Hilfsmethode, um den Namen eines Feiertags zu bekommen
    // Diese Methode sollte konsistent mit `calculateHolidaysForCache` sein.
    public String getHolidayName(LocalDate date, int year, String cantonAbbreviation) {
        LocalDate easterSunday = calculateEasterSunday(year);

        if (date.equals(LocalDate.of(year, 1, 1))) return "Neujahr";
        if (date.equals(easterSunday.minusDays(2))) return "Karfreitag";
        if (date.equals(easterSunday)) return "Ostersonntag"; // Obwohl Ostersonntag selbst oft kein "freier Tag" im Sinne von Arbeitsrecht ist, da eh Sonntag
        if (date.equals(easterSunday.plusDays(1))) return "Ostermontag";
        if (date.equals(LocalDate.of(year, 5, 1))) return "Tag der Arbeit";
        if (date.equals(easterSunday.plusDays(39))) return "Auffahrt";
        if (date.equals(easterSunday.plusDays(49))) return "Pfingstsonntag"; // Analog Ostersonntag
        if (date.equals(easterSunday.plusDays(50))) return "Pfingstmontag";
        if (date.equals(LocalDate.of(year, 8, 1))) return "Nationalfeiertag";
        if (date.equals(LocalDate.of(year, 12, 25))) return "Weihnachten";
        if (date.equals(LocalDate.of(year, 12, 26))) return "Stephanstag";

        // Kantonsspezifische Namen
        if (cantonAbbreviation != null && !cantonAbbreviation.trim().isEmpty()) {
            String cantonUpper = cantonAbbreviation.toUpperCase();
            if ("SG".equals(cantonUpper)) {
                if (date.equals(LocalDate.of(year, 1, 2))) return "Berchtoldstag (SG)";
                if (date.equals(LocalDate.of(year, 11, 1))) return "Allerheiligen (SG)";
            } else if ("ZH".equals(cantonUpper)) {
                if (date.equals(LocalDate.of(year, 1, 2))) return "Berchtoldstag (ZH)";
                // Ggf. weitere Namen für ZH
            }
            // Fügen Sie hier weitere Namen für kantonale Feiertage hinzu
        }
        // Fallback, falls kein spezifischer Name gefunden wird, aber als Feiertag gilt
        if (getHolidays(year, cantonAbbreviation).contains(date)) {
            return "Feiertag";
        }

        return "Unbekannter Feiertag"; // Sollte nicht oft erreicht werden
    }
}