package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationServiceTest {

    @Mock
    private VacationRequestRepository vacationRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private WorkScheduleService workScheduleService;
    @Mock
    private HolidayService holidayService;
    @Mock
    private ExternalNotificationService externalNotificationService;

    @InjectMocks
    private VacationService vacationService;

    private User admin;
    private User employee;
    private VacationRequest existingRequest;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(7L);

        admin = new User();
        admin.setUsername("admin");
        admin.setCompany(company);
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(new Role("ROLE_ADMIN"));
        admin.setRoles(adminRoles);

        employee = new User();
        employee.setId(42L);
        employee.setUsername("worker");
        employee.setCompany(company);
        employee.setIsHourly(false);
        employee.setIsPercentage(true);
        employee.setTrackingBalanceInMinutes(600);

        existingRequest = new VacationRequest();
        existingRequest.setId(10L);
        existingRequest.setUser(employee);
        existingRequest.setStartDate(LocalDate.of(2024, 2, 1));
        existingRequest.setEndDate(LocalDate.of(2024, 2, 2));
        existingRequest.setHalfDay(false);
        existingRequest.setUsesOvertime(true);
        existingRequest.setApproved(true);
        existingRequest.setDenied(false);
        existingRequest.setCompanyVacation(false);
        existingRequest.setOvertimeDeductionMinutes(480);
    }

    @Test
    void adminUpdateVacation_restoresAndReappliesOvertimeBalance() {
        when(vacationRequestRepository.findById(10L)).thenReturn(Optional.of(existingRequest));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(vacationRequestRepository.save(any(VacationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workScheduleService.isDayOff(eq(employee), any(LocalDate.class))).thenReturn(false);

        VacationRequest updated = vacationService.adminUpdateVacation(
                10L,
                "admin",
                LocalDate.of(2024, 2, 5),
                LocalDate.of(2024, 2, 6),
                null,
                true,
                360,
                true,
                null
        );

        assertEquals(LocalDate.of(2024, 2, 5), updated.getStartDate());
        assertEquals(LocalDate.of(2024, 2, 6), updated.getEndDate());
        assertThat(updated.isUsesOvertime()).isTrue();
        assertThat(updated.getOvertimeDeductionMinutes()).isEqualTo(360);
        assertThat(employee.getTrackingBalanceInMinutes()).isEqualTo(720);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).findByUsername("admin");
        verify(vacationRequestRepository).findById(10L);
        verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
        assertThat(userCaptor.getAllValues()).hasSize(2);
        assertThat(userCaptor.getAllValues().get(1).getTrackingBalanceInMinutes()).isEqualTo(720);
    }

    @Test
    void adminUpdateVacation_throwsWhenAdminMissing() {
        when(vacationRequestRepository.findById(10L)).thenReturn(Optional.of(existingRequest));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> vacationService.adminUpdateVacation(
                10L,
                "admin",
                existingRequest.getStartDate(),
                existingRequest.getEndDate(),
                null,
                null,
                null,
                null,
                null
        ));
    }
}
