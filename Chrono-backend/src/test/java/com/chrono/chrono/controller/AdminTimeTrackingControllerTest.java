package com.chrono.chrono.controller;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTimeTrackingControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeTrackingService timeTrackingService;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private Principal principal;

    private AdminTimeTrackingController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminTimeTrackingController();
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "timeTrackingService", timeTrackingService);
        ReflectionTestUtils.setField(controller, "userPermissionService", userPermissionService);
    }

    @Test
    void getAllTimeTrackSummaries_superAdminUsesTimeOverviewUsersOnly() {
        when(principal.getName()).thenReturn("superadmin");

        User superAdmin = new User();
        superAdmin.setUsername("superadmin");
        superAdmin.setRoles(Set.of(new Role("ROLE_SUPERADMIN")));

        User worker = new User();
        worker.setUsername("worker");
        worker.setRoles(Set.of(new Role("ROLE_USER")));

        DailyTimeSummaryDTO summary = new DailyTimeSummaryDTO(
                "worker",
                LocalDate.of(2026, 5, 5),
                480,
                30,
                List.of(),
                null,
                false,
                new DailyTimeSummaryDTO.PrimaryTimes(LocalTime.of(9, 0), LocalTime.of(17, 0), false)
        );

        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));
        when(userRepository.findTimeOverviewUsersDeletedFalse()).thenReturn(List.of(worker));
        when(timeTrackingService.getUserHistory("worker")).thenReturn(List.of(summary));

        ResponseEntity<?> response = controller.getAllTimeTrackSummaries(principal);

        assertEquals(200, response.getStatusCode().value());
        List<?> body = (List<?>) response.getBody();
        assertEquals(1, body.size());
        verify(userRepository).findTimeOverviewUsersDeletedFalse();
        verify(userRepository, never()).findByDeletedFalse();
        verify(timeTrackingService, never()).getUserHistory("superadmin");
    }
}
