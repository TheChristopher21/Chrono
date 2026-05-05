package com.chrono.chrono.services;

import com.chrono.chrono.dto.CompanyHolidayPreferenceDTO;
import com.chrono.chrono.dto.HolidayCatalogItemDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyHolidayPreference;
import com.chrono.chrono.repositories.CompanyHolidayPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    private static final Logger logger = LoggerFactory.getLogger(HolidayService.class);

    private final Map<Integer, Map<String, Set<LocalDate>>> holidayCache = new HashMap<>();
    private final Map<Integer, Set<LocalDate>> nationalHolidayCache = new HashMap<>();

    @Autowired
    private CompanyHolidayPreferenceRepository companyHolidayPreferenceRepository;

    private interface DateRule {
        LocalDate resolve(int year, LocalDate easterSunday);
    }

    private record HolidayDefinition(String code, String name, String country, String regionHint, DateRule dateRule) {
        LocalDate date(int year) {
            return dateRule.resolve(year, calculateEasterSundayStatic(year));
        }
    }

    public record HolidayMatch(LocalDate date, String name, boolean halfDay) {}

    private static final List<HolidayDefinition> HOLIDAY_CATALOG = List.of(
            new HolidayDefinition("CH_NEUJAHR", "Neujahr", "CH", "Schweiz", fixed(1, 1)),
            new HolidayDefinition("CH_BERCHTOLDSTAG", "Berchtoldstag", "CH", "kantonal", fixed(1, 2)),
            new HolidayDefinition("CH_DREIKOENIGE", "Heilige Drei Könige", "CH", "kantonal", fixed(1, 6)),
            new HolidayDefinition("CH_JOSEFSTAG", "Josefstag", "CH", "kantonal", fixed(3, 19)),
            new HolidayDefinition("CH_KARFREITAG", "Karfreitag", "CH", "kantonal verbreitet", easterOffset(-2)),
            new HolidayDefinition("CH_OSTERMONTAG", "Ostermontag", "CH", "kantonal verbreitet", easterOffset(1)),
            new HolidayDefinition("CH_TAG_DER_ARBEIT", "Tag der Arbeit", "CH", "kantonal", fixed(5, 1)),
            new HolidayDefinition("CH_AUFFAHRT", "Auffahrt", "CH", "Schweiz", easterOffset(39)),
            new HolidayDefinition("CH_PFINGSTMONTAG", "Pfingstmontag", "CH", "kantonal verbreitet", easterOffset(50)),
            new HolidayDefinition("CH_FRONLEICHNAM", "Fronleichnam", "CH", "kantonal", easterOffset(60)),
            new HolidayDefinition("CH_PETER_UND_PAUL", "Peter und Paul", "CH", "kantonal", fixed(6, 29)),
            new HolidayDefinition("CH_NATIONALFEIERTAG", "Nationalfeiertag", "CH", "Schweiz", fixed(8, 1)),
            new HolidayDefinition("CH_MARIA_HIMMELFAHRT", "Mariä Himmelfahrt", "CH", "kantonal", fixed(8, 15)),
            new HolidayDefinition("CH_BUSS_UND_BETTAG", "Eidg. Dank-, Buss- und Bettag", "CH", "kantonal", swissBettag()),
            new HolidayDefinition("CH_BETTAGSMONTAG", "Bettagsmontag", "CH", "kantonal", (year, easter) -> swissBettag().resolve(year, easter).plusDays(1)),
            new HolidayDefinition("CH_ALLERHEILIGEN", "Allerheiligen", "CH", "kantonal", fixed(11, 1)),
            new HolidayDefinition("CH_MARIA_EMPFAENGNIS", "Mariä Empfängnis", "CH", "kantonal", fixed(12, 8)),
            new HolidayDefinition("CH_WEIHNACHTEN", "Weihnachten", "CH", "Schweiz", fixed(12, 25)),
            new HolidayDefinition("CH_STEPHANSTAG", "Stephanstag", "CH", "kantonal verbreitet", fixed(12, 26)),
            new HolidayDefinition("CH_RESTAURATION_GENF", "Restauration der Republik", "CH", "Genf", fixed(12, 31)),

            new HolidayDefinition("DE_NEUJAHR", "Neujahr", "DE", "bundesweit", fixed(1, 1)),
            new HolidayDefinition("DE_HEILIGE_DREI_KOENIGE", "Heilige Drei Könige", "DE", "BW, BY, ST", fixed(1, 6)),
            new HolidayDefinition("DE_FRAUENTAG", "Internationaler Frauentag", "DE", "BE, MV", fixed(3, 8)),
            new HolidayDefinition("DE_KARFREITAG", "Karfreitag", "DE", "bundesweit", easterOffset(-2)),
            new HolidayDefinition("DE_OSTERSONNTAG", "Ostersonntag", "DE", "BB", easterOffset(0)),
            new HolidayDefinition("DE_OSTERMONTAG", "Ostermontag", "DE", "bundesweit", easterOffset(1)),
            new HolidayDefinition("DE_TAG_DER_ARBEIT", "Tag der Arbeit", "DE", "bundesweit", fixed(5, 1)),
            new HolidayDefinition("DE_CHRISTI_HIMMELFAHRT", "Christi Himmelfahrt", "DE", "bundesweit", easterOffset(39)),
            new HolidayDefinition("DE_PFINGSTSONNTAG", "Pfingstsonntag", "DE", "BB", easterOffset(49)),
            new HolidayDefinition("DE_PFINGSTMONTAG", "Pfingstmontag", "DE", "bundesweit", easterOffset(50)),
            new HolidayDefinition("DE_FRONLEICHNAM", "Fronleichnam", "DE", "BW, BY, HE, NW, RP, SL, regional SN/TH", easterOffset(60)),
            new HolidayDefinition("DE_AUGSBURGER_FRIEDENSFEST", "Augsburger Friedensfest", "DE", "Augsburg", fixed(8, 8)),
            new HolidayDefinition("DE_MARIA_HIMMELFAHRT", "Mariä Himmelfahrt", "DE", "SL, regional BY", fixed(8, 15)),
            new HolidayDefinition("DE_WELTKINDERTAG", "Weltkindertag", "DE", "TH", fixed(9, 20)),
            new HolidayDefinition("DE_TAG_DER_DEUTSCHEN_EINHEIT", "Tag der Deutschen Einheit", "DE", "bundesweit", fixed(10, 3)),
            new HolidayDefinition("DE_REFORMATIONSTAG", "Reformationstag", "DE", "BB, HB, HH, MV, NI, SN, ST, SH, TH", fixed(10, 31)),
            new HolidayDefinition("DE_ALLERHEILIGEN", "Allerheiligen", "DE", "BW, BY, NW, RP, SL", fixed(11, 1)),
            new HolidayDefinition("DE_BUSS_UND_BETTAG", "Buß- und Bettag", "DE", "SN", germanBussUndBettag()),
            new HolidayDefinition("DE_ERSTER_WEIHNACHTSTAG", "1. Weihnachtstag", "DE", "bundesweit", fixed(12, 25)),
            new HolidayDefinition("DE_ZWEITER_WEIHNACHTSTAG", "2. Weihnachtstag", "DE", "bundesweit", fixed(12, 26))
    );

    private static DateRule fixed(int month, int day) {
        return (year, easter) -> LocalDate.of(year, month, day);
    }

    private static DateRule easterOffset(int days) {
        return (year, easter) -> easter.plusDays(days);
    }

    private static DateRule swissBettag() {
        return (year, easter) -> LocalDate.of(year, 9, 1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .plusWeeks(2);
    }

    private static DateRule germanBussUndBettag() {
        return (year, easter) -> LocalDate.of(year, 11, 23)
                .with(TemporalAdjusters.previous(DayOfWeek.WEDNESDAY));
    }

    public List<HolidayCatalogItemDTO> getHolidayCatalog() {
        return HOLIDAY_CATALOG.stream()
                .map(def -> new HolidayCatalogItemDTO(def.code(), def.name(), def.country(), def.regionHint()))
                .toList();
    }

    public Set<LocalDate> getHolidays(int year, String cantonAbbreviation) {
        String cantonKey = (cantonAbbreviation == null || cantonAbbreviation.trim().isEmpty()) ? "GENERAL" : cantonAbbreviation.toUpperCase();

        return holidayCache
                .computeIfAbsent(year, k -> new HashMap<>())
                .computeIfAbsent(cantonKey, k -> calculateHolidaysForCache(year, cantonAbbreviation));
    }

    public Set<LocalDate> getNationalHolidays(int year) {
        return nationalHolidayCache.computeIfAbsent(year, this::calculateStrictNationalHolidays);
    }

    private Set<LocalDate> calculateHolidaysForCache(int year, String cantonAbbreviation) {
        Set<LocalDate> holidays = new HashSet<>();
        LocalDate easterSunday = calculateEasterSunday(year);

        holidays.add(LocalDate.of(year, 1, 1));
        holidays.add(easterSunday.plusDays(39));
        holidays.add(LocalDate.of(year, 8, 1));
        holidays.add(LocalDate.of(year, 12, 25));

        holidays.add(easterSunday.minusDays(2));
        holidays.add(easterSunday.plusDays(1));
        holidays.add(easterSunday.plusDays(50));
        holidays.add(LocalDate.of(year, 12, 26));
        holidays.add(LocalDate.of(year, 5, 1));

        if (cantonAbbreviation != null && !cantonAbbreviation.trim().isEmpty()) {
            String cantonUpper = cantonAbbreviation.toUpperCase();
            switch (cantonUpper) {
                case "SG":
                    holidays.add(LocalDate.of(year, 1, 2));
                    holidays.add(LocalDate.of(year, 11, 1));
                    break;
                case "ZH":
                    holidays.add(LocalDate.of(year, 1, 2));
                    holidays.add(LocalDate.of(year, 11, 1));
                    break;
                default:
                    logger.debug("Keine spezifischen kantonalen Feiertage für Kürzel '{}' im Jahr {} definiert. Verwende allgemeine Feiertage.", cantonUpper, year);
                    break;
            }
        }
        logger.info("Calculated holidays for year {} and canton '{}': {} days", year, cantonAbbreviation, holidays.size());
        return holidays;
    }

    private Set<LocalDate> calculateStrictNationalHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(year, 1, 1));
        LocalDate easterSunday = calculateEasterSunday(year);
        holidays.add(easterSunday.plusDays(39));
        holidays.add(LocalDate.of(year, 8, 1));
        holidays.add(LocalDate.of(year, 12, 25));
        return holidays;
    }

    public boolean isHoliday(LocalDate date, String cantonAbbreviation) {
        if (date == null) {
            return false;
        }
        return getHolidays(date.getYear(), cantonAbbreviation).contains(date);
    }

    public boolean isCompanyHoliday(LocalDate date, Company company) {
        return getHolidayMatch(date, company).isPresent();
    }

    public boolean isHalfDayHoliday(LocalDate date, Company company) {
        return getHolidayMatch(date, company)
                .map(HolidayMatch::halfDay)
                .orElse(false);
    }

    public Optional<HolidayMatch> getHolidayMatch(LocalDate date, Company company) {
        if (date == null) {
            return Optional.empty();
        }

        if (company != null && company.isCustomHolidaySelectionEnabled()) {
            return companyHolidayPreferenceRepository.findByCompany_Id(company.getId()).stream()
                    .map(pref -> findDefinition(pref.getHolidayCode())
                            .filter(def -> def.date(date.getYear()).equals(date))
                            .map(def -> new HolidayMatch(date, def.name(), pref.isHalfDay())))
                    .flatMap(Optional::stream)
                    .findFirst();
        }

        String cantonAbbreviation = company != null ? company.getCantonAbbreviation() : null;
        if (isHoliday(date, cantonAbbreviation)) {
            return Optional.of(new HolidayMatch(date, getHolidayName(date, date.getYear(), cantonAbbreviation), false));
        }
        return Optional.empty();
    }

    public boolean isGenerallyConsideredHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return getHolidays(date.getYear(), (String) null).contains(date);
    }

    public Map<LocalDate, String> getHolidayDetails(int year, String cantonAbbreviation, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, String> holidayDetails = new HashMap<>();
        Set<Integer> yearsToConsider = new HashSet<>();
        yearsToConsider.add(startDate.getYear());
        yearsToConsider.add(endDate.getYear());

        for (Integer currentYear : yearsToConsider) {
            Set<LocalDate> holidaysInYear = getHolidays(currentYear, cantonAbbreviation);
            holidaysInYear.stream()
                    .filter(holiday -> !holiday.isBefore(startDate) && !holiday.isAfter(endDate))
                    .sorted()
                    .forEach(holiday -> holidayDetails.put(holiday, getHolidayName(holiday, currentYear, cantonAbbreviation)));
        }
        return holidayDetails;
    }

    public Map<LocalDate, String> getHolidayDetails(Company company, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, String> holidayDetails = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            getHolidayMatch(date, company).ifPresent(match -> {
                String name = match.halfDay() ? match.name() + " (halbtags)" : match.name();
                holidayDetails.put(match.date(), name);
            });
        }
        return holidayDetails;
    }

    public Set<LocalDate> getCompanyHolidays(int year, Company company) {
        if (company != null && company.isCustomHolidaySelectionEnabled()) {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            return getHolidayDetails(company, start, end).keySet();
        }
        String cantonAbbreviation = company != null ? company.getCantonAbbreviation() : null;
        return getHolidays(year, cantonAbbreviation);
    }

    public String getHolidayName(LocalDate date, int year, String cantonAbbreviation) {
        LocalDate easterSunday = calculateEasterSunday(year);

        if (date.equals(LocalDate.of(year, 1, 1))) return "Neujahr";
        if (date.equals(easterSunday.minusDays(2))) return "Karfreitag";
        if (date.equals(easterSunday)) return "Ostersonntag";
        if (date.equals(easterSunday.plusDays(1))) return "Ostermontag";
        if (date.equals(LocalDate.of(year, 5, 1))) return "Tag der Arbeit";
        if (date.equals(easterSunday.plusDays(39))) return "Auffahrt";
        if (date.equals(easterSunday.plusDays(49))) return "Pfingstsonntag";
        if (date.equals(easterSunday.plusDays(50))) return "Pfingstmontag";
        if (date.equals(LocalDate.of(year, 8, 1))) return "Nationalfeiertag";
        if (date.equals(LocalDate.of(year, 12, 25))) return "Weihnachten";
        if (date.equals(LocalDate.of(year, 12, 26))) return "Stephanstag";

        if (cantonAbbreviation != null && !cantonAbbreviation.trim().isEmpty()) {
            String cantonUpper = cantonAbbreviation.toUpperCase();
            if ("SG".equals(cantonUpper)) {
                if (date.equals(LocalDate.of(year, 1, 2))) return "Berchtoldstag (SG)";
                if (date.equals(LocalDate.of(year, 11, 1))) return "Allerheiligen (SG)";
            } else if ("ZH".equals(cantonUpper)) {
                if (date.equals(LocalDate.of(year, 1, 2))) return "Berchtoldstag (ZH)";
            }
        }
        if (getHolidays(year, cantonAbbreviation).contains(date)) {
            return "Feiertag";
        }

        return "Unbekannter Feiertag";
    }

    public List<CompanyHolidayPreferenceDTO> getCompanyHolidayPreferences(Company company) {
        if (company == null) {
            return List.of();
        }
        List<CompanyHolidayPreference> preferences = company.getId() == null
                ? List.of()
                : companyHolidayPreferenceRepository.findByCompany_Id(company.getId());
        if (!company.isCustomHolidaySelectionEnabled() && preferences.isEmpty()) {
            return getLegacyDefaultPreferences(company);
        }
        return preferences.stream()
                .sorted(Comparator.comparing(pref -> catalogSortIndex(pref.getHolidayCode())))
                .map(pref -> new CompanyHolidayPreferenceDTO(pref.getHolidayCode(), pref.isHalfDay()))
                .toList();
    }

    @Transactional
    public void replaceCompanyHolidayPreferences(Company company, List<CompanyHolidayPreferenceDTO> preferences) {
        if (company == null || company.getId() == null || preferences == null) {
            return;
        }

        Map<String, CompanyHolidayPreferenceDTO> validPreferences = preferences.stream()
                .filter(Objects::nonNull)
                .filter(pref -> pref.getHolidayCode() != null)
                .filter(pref -> findDefinition(pref.getHolidayCode()).isPresent())
                .collect(Collectors.toMap(
                        pref -> pref.getHolidayCode().trim().toUpperCase(),
                        pref -> new CompanyHolidayPreferenceDTO(pref.getHolidayCode().trim().toUpperCase(), pref.isHalfDay()),
                        (first, second) -> second,
                        LinkedHashMap::new
                ));

        companyHolidayPreferenceRepository.deleteByCompany_Id(company.getId());
        List<CompanyHolidayPreference> entities = validPreferences.values().stream()
                .map(pref -> new CompanyHolidayPreference(company, pref.getHolidayCode(), pref.isHalfDay()))
                .toList();
        companyHolidayPreferenceRepository.saveAll(entities);
    }

    private List<CompanyHolidayPreferenceDTO> getLegacyDefaultPreferences(Company company) {
        String cantonAbbreviation = (company.getCantonAbbreviation() == null || company.getCantonAbbreviation().trim().isEmpty())
                ? "SG"
                : company.getCantonAbbreviation();
        Set<LocalDate> legacyDates = getHolidays(2026, cantonAbbreviation);
        return HOLIDAY_CATALOG.stream()
                .filter(def -> "CH".equals(def.country()))
                .filter(def -> legacyDates.contains(def.date(2026)))
                .map(def -> new CompanyHolidayPreferenceDTO(def.code(), false))
                .toList();
    }

    private Optional<HolidayDefinition> findDefinition(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return HOLIDAY_CATALOG.stream()
                .filter(def -> def.code().equals(normalized))
                .findFirst();
    }

    private int catalogSortIndex(String code) {
        for (int i = 0; i < HOLIDAY_CATALOG.size(); i++) {
            if (HOLIDAY_CATALOG.get(i).code().equals(code)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private LocalDate calculateEasterSunday(int year) {
        return calculateEasterSundayStatic(year);
    }

    private static LocalDate calculateEasterSundayStatic(int year) {
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
}
