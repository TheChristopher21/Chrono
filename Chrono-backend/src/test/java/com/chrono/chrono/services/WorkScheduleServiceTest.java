package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserHolidayOption;
import com.chrono.chrono.entities.UserScheduleRule;

import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.UserHolidayOptionRepository;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WorkScheduleServiceTest {

    @Mock
    private UserScheduleRuleRepository ruleRepo;

    @Mock

    private HolidayService holidayService;

    @Mock
    private SickLeaveRepository sickLeaveRepository;

    @Mock
    private UserHolidayOptionRepository userHolidayOptionRepository;

    @InjectMocks
    private WorkScheduleService workScheduleService;

    @Test
    void getExpectedWorkHours_returnsZeroBeforeEntryDate() {
        User user = new User();
        user.setEntryDate(LocalDate.of(2026, 5, 1));
        user.setDailyWorkHours(8.5);

        double hours = workScheduleService.getExpectedWorkHours(user, LocalDate.of(2026, 4, 30));

        assertEquals(0.0, hours);
    }

    @Test
    void getExpectedWeeklyMinutesForPercentageUser_skipsDatesBeforeEntry() {
        User user = new User();
        user.setUsername("percentage-worker");
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(5);
        user.setEntryDate(LocalDate.of(2026, 3, 25));
        LocalDate monday = LocalDate.of(2026, 3, 23);

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, monday, monday.plusDays(4)))
                .thenReturn(Collections.emptyList());
        for (int i = 2; i <= 4; i++) {
            when(holidayService.isHoliday(monday.plusDays(i), null)).thenReturn(false);
        }

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(
                user,
                monday,
                monday.plusDays(4),
                Collections.emptyList()
        );

        assertEquals(918, minutes);
    }

    @Test
    void getExpectedWorkHours_returnsHalfForHalfDayRule() {
        User user = new User();
        user.setDailyWorkHours(8.0);
        LocalDate date = LocalDate.of(2024, 1, 1);

        UserScheduleRule rule = new UserScheduleRule();
        rule.setDayMode("HALF_DAY");
        rule.setStartDate(date.minusDays(1));
        rule.setDayOfWeek(date.getDayOfWeek().getValue());

        when(ruleRepo.findByUser(user)).thenReturn(List.of(rule));

        double hours = workScheduleService.getExpectedWorkHours(user, date);

        assertEquals(4.0, hours);
    }

    @Test
    void computeExpectedWorkMinutes_accountsForHalfDayRule() {
        User user = new User();
        user.setDailyWorkHours(8.0);
        LocalDate date = LocalDate.of(2024, 1, 1);

        UserScheduleRule rule = new UserScheduleRule();
        rule.setDayMode("HALF_DAY");
        rule.setStartDate(date.minusDays(1));
        rule.setDayOfWeek(date.getDayOfWeek().getValue());

        when(ruleRepo.findByUser(user)).thenReturn(List.of(rule));
        when(holidayService.isHoliday(date, null)).thenReturn(false);
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, date, Collections.emptyList());

        assertEquals(240, minutes);

    }
    @Test
    void getExpectedWeeklyMinutesForPercentageUser_limitsExpectedMinutesToEvaluationEnd() {
        User user = new User();
        user.setUsername("gabriela");
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(5);

        LocalDate monday = LocalDate.of(2026, 3, 23);

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, monday, monday)).thenReturn(Collections.emptyList());
        when(holidayService.isHoliday(monday, null)).thenReturn(false);

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, monday, monday, Collections.emptyList());

        assertEquals(306, minutes);
    }

    @Test
    void getExpectedWeeklyMinutesForPercentageUser_respectsEvaluationStartWithinWeek() {
        User user = new User();
        user.setUsername("gabriela");
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(5);

        LocalDate monday = LocalDate.of(2026, 3, 23);
        LocalDate wednesday = monday.plusDays(2);
        LocalDate friday = monday.plusDays(4);

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, wednesday, friday)).thenReturn(Collections.emptyList());
        for (int i = 2; i <= 4; i++) {
            when(holidayService.isHoliday(monday.plusDays(i), null)).thenReturn(false);
        }

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, monday, wednesday, friday, Collections.emptyList());

        assertEquals(918, minutes);
    }


    @Test
    void getExpectedWeeklyMinutesForPercentageUser_countsOnlyConfiguredWorkDays() {
        User user = new User();
        user.setUsername("gabriela");
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(3);

        LocalDate monday = LocalDate.of(2026, 3, 23);

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, monday, monday.plusDays(4))).thenReturn(Collections.emptyList());
        for (int i = 0; i < 3; i++) {
            when(holidayService.isHoliday(monday.plusDays(i), null)).thenReturn(false);
        }

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, monday, monday.plusDays(4), Collections.emptyList());

        assertEquals(1530, minutes);
    }

    @Test
    void computeExpectedWorkMinutes_returnsZeroOutsideConfiguredPercentageWorkDays() {
        User user = new User();
        user.setUsername("gabriela");
        user.setIsPercentage(true);
        user.setWorkPercentage(60);
        user.setExpectedWorkDays(3);

        LocalDate thursday = LocalDate.of(2026, 3, 26);

        when(holidayService.isHoliday(thursday, null)).thenReturn(false);
        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, thursday, Collections.emptyList());

        assertEquals(0, minutes);
    }

    @Test
    void getExpectedWorkHours_keepsLegacyStGallenHolidayForExistingCompany() {
        Company company = new Company("Chrono AG");
        company.setCantonAbbreviation("SG");
        company.setCustomHolidaySelectionEnabled(false);

        User user = new User();
        user.setUsername("maria");
        user.setCompany(company);
        user.setDailyWorkHours(8.5);

        LocalDate berchtoldstag = LocalDate.of(2026, 1, 2);

        when(holidayService.isHoliday(berchtoldstag, "SG")).thenReturn(true);
        when(ruleRepo.findByUser(user)).thenReturn(Collections.emptyList());

        double hours = workScheduleService.getExpectedWorkHours(user, berchtoldstag);

        assertEquals(0.0, hours);
    }

    @Test
    void getExpectedWorkHours_halvesCustomCompanyHalfDayHoliday() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCustomHolidaySelectionEnabled(true);

        User user = new User();
        user.setUsername("maria");
        user.setCompany(company);
        user.setDailyWorkHours(8.5);

        LocalDate holiday = LocalDate.of(2026, 4, 3);

        when(holidayService.isCompanyHoliday(holiday, company)).thenReturn(true);
        when(holidayService.isHalfDayHoliday(holiday, company)).thenReturn(true);
        when(ruleRepo.findByUser(user)).thenReturn(Collections.emptyList());

        double hours = workScheduleService.getExpectedWorkHours(user, holiday);

        assertEquals(4.25, hours);
    }

    @Test
    void getExpectedWeeklyMinutesForPercentageUser_preservesSavedDoNotDeductOption() {
        User user = percentageUserWithStGallenCompany();
        LocalDate berchtoldstag = LocalDate.of(2026, 1, 2);
        UserHolidayOption option = new UserHolidayOption(
                user,
                berchtoldstag,
                UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET
        );

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, berchtoldstag, berchtoldstag)).thenReturn(List.of(option));
        when(holidayService.isHoliday(berchtoldstag, "SG")).thenReturn(true);

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, berchtoldstag, berchtoldstag, berchtoldstag, Collections.emptyList());

        assertEquals(510, minutes);
    }

    @Test
    void getExpectedWeeklyMinutesForPercentageUser_preservesSavedDeductOption() {
        User user = percentageUserWithStGallenCompany();
        LocalDate berchtoldstag = LocalDate.of(2026, 1, 2);
        UserHolidayOption option = new UserHolidayOption(
                user,
                berchtoldstag,
                UserHolidayOption.HolidayHandlingOption.DEDUCT_FROM_WEEKLY_TARGET
        );

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, berchtoldstag, berchtoldstag)).thenReturn(List.of(option));
        when(holidayService.isHoliday(berchtoldstag, "SG")).thenReturn(true);

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, berchtoldstag, berchtoldstag, berchtoldstag, Collections.emptyList());

        assertEquals(0, minutes);
    }

    @Test
    void getExpectedWeeklyMinutesForPercentageUser_deductsHalfForSavedDeductOnHalfDayHoliday() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCustomHolidaySelectionEnabled(true);

        User user = new User();
        user.setUsername("gabriela");
        user.setCompany(company);
        user.setIsPercentage(true);
        user.setWorkPercentage(100);
        user.setExpectedWorkDays(5);

        LocalDate holiday = LocalDate.of(2026, 4, 3);
        UserHolidayOption option = new UserHolidayOption(
                user,
                holiday,
                UserHolidayOption.HolidayHandlingOption.DEDUCT_FROM_WEEKLY_TARGET
        );

        when(sickLeaveRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, holiday, holiday)).thenReturn(List.of(option));
        when(holidayService.isCompanyHoliday(holiday, company)).thenReturn(true);
        when(holidayService.isHalfDayHoliday(holiday, company)).thenReturn(true);

        int minutes = workScheduleService.getExpectedWeeklyMinutesForPercentageUser(user, holiday, holiday, holiday, Collections.emptyList());

        assertEquals(255, minutes);
    }

    private User percentageUserWithStGallenCompany() {
        Company company = new Company("Chrono AG");
        company.setCantonAbbreviation("SG");
        company.setCustomHolidaySelectionEnabled(false);

        User user = new User();
        user.setUsername("gabriela");
        user.setCompany(company);
        user.setIsPercentage(true);
        user.setWorkPercentage(100);
        user.setExpectedWorkDays(5);
        return user;
    }

}
