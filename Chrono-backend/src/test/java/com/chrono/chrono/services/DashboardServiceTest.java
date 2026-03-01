package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TimeTrackingService timeTrackingService;

    @InjectMocks
    private DashboardService dashboardService;

    private User user;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("demo-admin");
        user.setRoles(Set.of(new Role("ROLE_ADMIN")));
        user.setIsHourly(false);
        user.setIsPercentage(false);
        start = LocalDate.of(2024, 1, 1);
        end = LocalDate.of(2024, 1, 7);
    }

    @Test
    void getUserDashboardForWeek_filtersSummariesWithinRange() {
        DailyTimeSummaryDTO monday = summary("demo-admin", LocalDate.of(2024, 1, 1), 480, 60);
        DailyTimeSummaryDTO friday = summary("demo-admin", LocalDate.of(2024, 1, 5), 510, 45);
        DailyTimeSummaryDTO nextWeek = summary("demo-admin", LocalDate.of(2024, 1, 8), 300, 30);

        when(userRepository.findByUsername("demo-admin")).thenReturn(Optional.of(user));
        when(timeTrackingService.getUserHistory("demo-admin"))
                .thenReturn(List.of(monday, friday, nextWeek));

        DashboardResponse response = dashboardService.getUserDashboardForWeek("demo-admin", start, end);

        assertEquals("demo-admin", response.getUsername());
        assertEquals("ROLE_ADMIN", response.getRoleName());
        assertEquals(2, response.getDailySummaries().size());
        assertTrue(response.getDailySummaries().containsAll(List.of(monday, friday)));
        assertTrue(response.getDailySummaries().stream().noneMatch(dto -> dto.getDate().isAfter(end)));
        verify(timeTrackingService).getUserHistory("demo-admin");
    }

    @Test
    void getUserDashboardForWeek_includesBoundaryDatesAndHourlyTotals() {
        user.setIsHourly(true);
        DailyTimeSummaryDTO startDay = summary("demo-admin", start, 450, 30);
        DailyTimeSummaryDTO endDay = summary("demo-admin", end, 420, 60);

        when(userRepository.findByUsername("demo-admin")).thenReturn(Optional.of(user));
        when(timeTrackingService.getUserHistory("demo-admin"))
                .thenReturn(List.of(startDay, endDay));

        DashboardResponse response = dashboardService.getUserDashboardForWeek("demo-admin", start, end);

        assertEquals(2, response.getDailySummaries().size());
        assertTrue(response.getDailySummaries().containsAll(List.of(startDay, endDay)));
        int weeklyWorkedMinutes = response.getDailySummaries().stream()
                .mapToInt(DailyTimeSummaryDTO::getWorkedMinutes)
                .sum();
        assertEquals(870, weeklyWorkedMinutes);
    }

    @Test
    void getUserDashboardForWeek_handlesPercentageUsers() {
        user.setIsPercentage(true);
        DailyTimeSummaryDTO midWeek = summary("demo-admin", LocalDate.of(2024, 1, 3), 360, 45);

        when(userRepository.findByUsername("demo-admin")).thenReturn(Optional.of(user));
        when(timeTrackingService.getUserHistory("demo-admin"))
                .thenReturn(List.of(midWeek));

        DashboardResponse response = dashboardService.getUserDashboardForWeek("demo-admin", start, end);

        assertEquals(1, response.getDailySummaries().size());
        assertEquals(midWeek, response.getDailySummaries().get(0));
        assertTrue(response.getDailySummaries().get(0).getWorkedMinutes() > 0);
    }

    @Test
    void getUserDashboardForWeek_supportsDemoUsersAndSetsFallbackRole() {
        user.setRoles(Set.of());
        user.setDemo(true);
        DailyTimeSummaryDTO demoDay = summary("demo-admin", LocalDate.of(2024, 1, 4), 540, 30);

        when(userRepository.findByUsername("demo-admin")).thenReturn(Optional.of(user));
        when(timeTrackingService.getUserHistory("demo-admin"))
                .thenReturn(List.of(demoDay));

        DashboardResponse response = dashboardService.getUserDashboardForWeek("demo-admin", start, end);

        assertEquals("NONE", response.getRoleName());
        assertEquals(1, response.getDailySummaries().size());
        assertEquals(demoDay, response.getDailySummaries().get(0));
    }

    @Test
    void getUserDashboardForWeek_returnsEmptySummariesWhenNoMatches() {
        DailyTimeSummaryDTO outsideRange = summary("demo-admin", LocalDate.of(2024, 2, 1), 300, 30);

        when(userRepository.findByUsername("demo-admin")).thenReturn(Optional.of(user));
        when(timeTrackingService.getUserHistory("demo-admin"))
                .thenReturn(List.of(outsideRange));

        DashboardResponse response = dashboardService.getUserDashboardForWeek("demo-admin", start, end);

        assertNotNull(response.getDailySummaries());
        assertTrue(response.getDailySummaries().isEmpty());
    }

    @Test
    void getUserDashboardForWeek_throwsWhenUserMissing() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                dashboardService.getUserDashboardForWeek("unknown", start, end));
    }

    private DailyTimeSummaryDTO summary(String username, LocalDate date, int workedMinutes, int breakMinutes) {
        return new DailyTimeSummaryDTO(
                username,
                date,
                workedMinutes,
                breakMinutes,
                List.of(),
                null,
                false,
                new DailyTimeSummaryDTO.PrimaryTimes(LocalTime.of(9, 0), LocalTime.of(17, 0), false)
        );
    }
}
