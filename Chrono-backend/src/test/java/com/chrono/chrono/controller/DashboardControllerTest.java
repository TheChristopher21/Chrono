package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void getUserDashboard_parsesIsoDatesForAllDashboardRoutes() {
        DashboardResponse expected = new DashboardResponse();
        when(dashboardService.getUserDashboardForWeek("demo-admin", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(expected);

        DashboardResponse actual = dashboardController.getUserDashboard("demo-admin", "2024-01-01", "2024-01-07");

        assertSame(expected, actual);
        verify(dashboardService).getUserDashboardForWeek("demo-admin", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));
    }
}
