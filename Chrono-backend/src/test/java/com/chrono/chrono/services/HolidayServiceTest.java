package com.chrono.chrono.services;

import com.chrono.chrono.dto.CompanyHolidayPreferenceDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyHolidayPreference;
import com.chrono.chrono.repositories.CompanyHolidayPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Test
    void concurrentFirstRequestsReturnCompleteImmutableHolidaySets() throws Exception {
        var service = new HolidayService();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(12);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Set<LocalDate>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 48; i++) {
                final int year = 2026 + i % 3;
                futures.add(executor.submit(() -> { gate.await(); return service.getHolidays(year, "ZH"); }));
            }
            gate.countDown();
            for (int i = 0; i < futures.size(); i++) {
                var days = futures.get(i).get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(service.getHolidays(2026 + i % 3, "ZH"), days);
                assertTrue(days.contains(LocalDate.of(2026 + i % 3, 8, 1)));
                org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, days::clear);
            }
        } finally { executor.shutdownNow(); }
    }

    @Mock
    private CompanyHolidayPreferenceRepository companyHolidayPreferenceRepository;

    @InjectMocks
    private HolidayService holidayService;

    @Test
    void getCompanyHolidayPreferences_defaultsExistingCompaniesToStGallenSelection() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCustomHolidaySelectionEnabled(false);

        when(companyHolidayPreferenceRepository.findByCompany_Id(1L)).thenReturn(List.of());

        Set<String> selectedCodes = holidayService.getCompanyHolidayPreferences(company).stream()
                .map(CompanyHolidayPreferenceDTO::getHolidayCode)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "CH_NEUJAHR",
                "CH_BERCHTOLDSTAG",
                "CH_KARFREITAG",
                "CH_OSTERMONTAG",
                "CH_TAG_DER_ARBEIT",
                "CH_AUFFAHRT",
                "CH_PFINGSTMONTAG",
                "CH_NATIONALFEIERTAG",
                "CH_ALLERHEILIGEN",
                "CH_WEIHNACHTEN",
                "CH_STEPHANSTAG"
        ), selectedCodes);
    }

    @Test
    void getCompanyHolidayPreferences_returnsSavedPreferencesInLegacyMode() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCustomHolidaySelectionEnabled(false);

        when(companyHolidayPreferenceRepository.findByCompany_Id(1L)).thenReturn(List.of(
                new CompanyHolidayPreference(company, "CH_NEUJAHR", false),
                new CompanyHolidayPreference(company, "CH_ALLERHEILIGEN", true)
        ));

        List<CompanyHolidayPreferenceDTO> preferences = holidayService.getCompanyHolidayPreferences(company);

        assertEquals(2, preferences.size());
        assertEquals("CH_NEUJAHR", preferences.get(0).getHolidayCode());
        assertFalse(preferences.get(0).isHalfDay());
        assertEquals("CH_ALLERHEILIGEN", preferences.get(1).getHolidayCode());
        assertTrue(preferences.get(1).isHalfDay());
    }

    @Test
    void isCompanyHoliday_keepsLegacyStGallenBehaviorWhenCustomSelectionIsDisabled() {
        Company company = new Company("Chrono AG");
        company.setCantonAbbreviation("SG");
        company.setCustomHolidaySelectionEnabled(false);

        LocalDate berchtoldstag = LocalDate.of(2026, 1, 2);

        assertTrue(holidayService.isCompanyHoliday(berchtoldstag, company));
        assertFalse(holidayService.isHalfDayHoliday(berchtoldstag, company));
    }

    @Test
    void isCompanyHoliday_usesCustomSelectionAndHalfDayFlagWhenEnabled() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCantonAbbreviation("SG");
        company.setCustomHolidaySelectionEnabled(true);

        when(companyHolidayPreferenceRepository.findByCompany_Id(1L)).thenReturn(List.of(
                new CompanyHolidayPreference(company, "CH_KARFREITAG", true)
        ));

        assertTrue(holidayService.isCompanyHoliday(LocalDate.of(2026, 4, 3), company));
        assertTrue(holidayService.isHalfDayHoliday(LocalDate.of(2026, 4, 3), company));
        assertFalse(holidayService.isCompanyHoliday(LocalDate.of(2026, 1, 2), company));
    }
}
