package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
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

}

