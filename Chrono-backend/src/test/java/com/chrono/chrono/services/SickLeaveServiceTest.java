package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.SickLeave;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.SickLeaveRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SickLeaveServiceTest {

    @Mock
    private SickLeaveRepository sickLeaveRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeTrackingService timeTrackingService;

    @InjectMocks
    private SickLeaveService sickLeaveService;

    private User admin;
    private User employee;
    private SickLeave sickLeave;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(8L);

        admin = new User();
        admin.setUsername("admin");
        admin.setCompany(company);
        Set<Role> roles = new HashSet<>();
        roles.add(new Role("ROLE_ADMIN"));
        admin.setRoles(roles);

        employee = new User();
        employee.setId(100L);
        employee.setUsername("worker");
        employee.setCompany(company);

        sickLeave = new SickLeave();
        sickLeave.setId(55L);
        sickLeave.setUser(employee);
        sickLeave.setStartDate(LocalDate.of(2024, 3, 1));
        sickLeave.setEndDate(LocalDate.of(2024, 3, 2));
        sickLeave.setHalfDay(false);
        sickLeave.setComment("Initial");
    }

    @Test
    void updateSickLeave_updatesFieldsAndRebuildsBalance() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(sickLeaveRepository.findById(55L)).thenReturn(Optional.of(sickLeave));
        when(sickLeaveRepository.save(any(SickLeave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate newDate = LocalDate.of(2024, 3, 5);

        SickLeave updated = sickLeaveService.updateSickLeave(
                55L,
                "admin",
                newDate,
                newDate,
                true,
                "Updated comment"
        );

        assertThat(updated.getStartDate()).isEqualTo(newDate);
        assertThat(updated.getEndDate()).isEqualTo(newDate);
        assertThat(updated.isHalfDay()).isTrue();
        assertThat(updated.getComment()).isEqualTo("Updated comment");
        verify(timeTrackingService).rebuildUserBalance(employee);
        verify(sickLeaveRepository).save(sickLeave);
    }

    @Test
    void updateSickLeave_throwsWhenAdminNotFound() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> sickLeaveService.updateSickLeave(
                55L,
                "missing",
                LocalDate.now(),
                LocalDate.now(),
                false,
                ""
        ));
    }
}
