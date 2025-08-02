package com.chrono.chrono.services;

import com.chrono.chrono.entities.SickLeave;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.UserHolidayOptionRepository;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkScheduleServiceTest {

    @Mock
    private HolidayService holidayService;

    @Mock
    private SickLeaveRepository sickLeaveRepository;

    @Mock
    private UserHolidayOptionRepository userHolidayOptionRepository;

    @Mock
    private UserScheduleRuleRepository ruleRepo;

    @InjectMocks
    private WorkScheduleService workScheduleService;

    private User createStandardUser() {
        User user = new User();
        user.setUsername("standard");
        user.setIsHourly(false);
        user.setIsPercentage(false);
        user.setDailyWorkHours(8.0);
        return user;
    }

    private User createPercentageUser() {
        User user = new User();
        user.setUsername("percent");
        user.setIsHourly(false);
        user.setIsPercentage(true);
        user.setWorkPercentage(50);
        user.setExpectedWorkDays(5);
        return user;
    }

    @Test
    void computeExpectedWorkMinutes_returnsZero_onHoliday() {
        User user = createStandardUser();
        LocalDate date = LocalDate.of(2024, 1, 1);
        when(holidayService.isHoliday(date, null)).thenReturn(true);
        when(sickLeaveRepository.findByUser(user)).thenReturn(List.of());

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, date, List.of());

        assertEquals(0, minutes);
        verify(holidayService).isHoliday(date, null);
    }

    @Test
    void computeExpectedWorkMinutes_halfDayVacation_returnsHalfOfDailyMinutes() {
        User user = createStandardUser();
        LocalDate date = LocalDate.of(2024, 1, 2);
        when(holidayService.isHoliday(date, null)).thenReturn(false);
        when(sickLeaveRepository.findByUser(user)).thenReturn(List.of());

        VacationRequest vr = new VacationRequest();
        vr.setApproved(true);
        vr.setHalfDay(true);
        vr.setStartDate(date);
        vr.setEndDate(date);

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, date, List.of(vr));

        assertEquals(240, minutes);
    }

    @Test
    void computeExpectedWorkMinutes_percentageUser_noAbsence_returnsProportionalMinutes() {
        User user = createPercentageUser();
        LocalDate date = LocalDate.of(2024, 1, 3);
        when(holidayService.isHoliday(date, null)).thenReturn(false);
        when(sickLeaveRepository.findByUser(user)).thenReturn(List.of());

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, date, List.of());

        assertEquals(255, minutes);
    }

    @Test
    void computeExpectedWorkMinutes_percentageUser_halfDaySick_returnsHalfProportionalMinutes() {
        User user = createPercentageUser();
        LocalDate date = LocalDate.of(2024, 1, 4);
        when(holidayService.isHoliday(date, null)).thenReturn(false);

        SickLeave sick = new SickLeave();
        sick.setStartDate(date);
        sick.setEndDate(date);
        sick.setHalfDay(true);

        when(sickLeaveRepository.findByUser(user)).thenReturn(List.of(sick));

        int minutes = workScheduleService.computeExpectedWorkMinutes(user, date, List.of());

        assertEquals(127, minutes);
    }
}

