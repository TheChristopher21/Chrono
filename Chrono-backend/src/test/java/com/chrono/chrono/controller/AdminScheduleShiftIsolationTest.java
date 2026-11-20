package com.chrono.chrono.controller;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.services.ShiftDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminScheduleShiftIsolationTest {
    @Test
    void durationsAndOverlapsResolveIdenticalKeysUsingTheTargetEmployeesCompany() {
        Company first = new Company("Praxis"); first.setId(17L);
        Company second = new Company("Other"); second.setId(29L);
        User firstUser = new User(); firstUser.setCompany(first);
        User secondUser = new User(); secondUser.setCompany(second);
        ScheduleRuleRepository repository = mock(ScheduleRuleRepository.class);
        when(repository.findByCompany_IdAndShiftKey(17L, "EARLY")).thenReturn(Optional.of(rule("08:00", "12:00")));
        when(repository.findByCompany_IdAndShiftKey(29L, "EARLY")).thenReturn(Optional.of(rule("08:00", "14:00")));
        when(repository.findByCompany_IdAndShiftKey(17L, "LATE")).thenReturn(Optional.of(rule("13:00", "16:00")));
        when(repository.findByCompany_IdAndShiftKey(29L, "LATE")).thenReturn(Optional.of(rule("13:00", "16:00")));
        AdminScheduleEntryController controller = new AdminScheduleEntryController();
        ReflectionTestUtils.setField(controller, "shiftDefinitionService", new ShiftDefinitionService(repository));
        Integer firstDuration = ReflectionTestUtils.invokeMethod(controller, "getShiftDurationMinutes", firstUser, "EARLY");
        Integer secondDuration = ReflectionTestUtils.invokeMethod(controller, "getShiftDurationMinutes", secondUser, "EARLY");
        Boolean firstOverlap = ReflectionTestUtils.invokeMethod(controller, "shiftsOverlap", firstUser, "EARLY", "LATE");
        Boolean secondOverlap = ReflectionTestUtils.invokeMethod(controller, "shiftsOverlap", secondUser, "EARLY", "LATE");
        assertEquals(240, firstDuration);
        assertEquals(360, secondDuration);
        assertFalse(firstOverlap);
        assertTrue(secondOverlap);
    }

    private ScheduleRule rule(String start, String end) {
        ScheduleRule rule = new ScheduleRule(); rule.setStartTime(start); rule.setEndTime(end); return rule;
    }
}
