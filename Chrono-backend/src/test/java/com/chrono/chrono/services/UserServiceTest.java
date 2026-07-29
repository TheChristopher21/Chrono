package com.chrono.chrono.services;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPermissionService userPermissionService;

    @InjectMocks
    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User();
        existingUser.setUsername("john");
        existingUser.setFirstName("John");
        existingUser.setLastName("Doe");
        existingUser.setEmail("john@example.com");
        existingUser.setEmailNotifications(true);
        existingUser.setAnnualVacationDays(20);
        existingUser.setIsHourly(true);
        existingUser.setIsPercentage(false);
        existingUser.setWorkPercentage(100);
        existingUser.setHourlyRate(20.0);
        existingUser.setMonthlySalary(3000.0);
        existingUser.setTrackingBalanceInMinutes(10);
    }

    @Test
    void getUserByUsername_returnsUser_whenExists() {
        existingUser.setTrackingBalanceInMinutes(null);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(existingUser));

        User result = userService.getUserByUsername("john");

        assertEquals(existingUser, result);
        assertEquals(0, result.getTrackingBalanceInMinutes());
        verify(userRepository).findByUsername("john");
    }

    @Test
    void getUserByUsername_throwsException_whenNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserByUsername("unknown"));
        verify(userRepository).findByUsername("unknown");
    }

    @Test
    void getUserProfileByUsername_buildsTheCompleteProfileInsideTheService() {
        Company company = new Company("Chrono Test");
        company.setId(7L);
        company.setCantonAbbreviation("ZH");
        company.setCustomerTrackingEnabled(true);
        company.setEnabledFeatures(Set.of("projects", "pms"));

        Customer lastCustomer = new Customer();
        lastCustomer.setId(11L);
        lastCustomer.setName("Hotelkunde");

        existingUser.setCompany(company);
        existingUser.setLastCustomer(lastCustomer);
        existingUser.setRoles(Set.of(new Role("ROLE_ADMIN")));
        Map<String, String> permissions = Map.of("pms", "MANAGE");

        when(userRepository.findByUsernameWithProfileContext("john"))
                .thenReturn(Optional.of(existingUser));
        when(userPermissionService.resolvePagePermissions(existingUser))
                .thenReturn(permissions);

        UserDTO profile = userService.getUserProfileByUsername("john");

        assertEquals(7L, profile.getCompanyId());
        assertEquals("ZH", profile.getCompanyCantonAbbreviation());
        assertEquals(11L, profile.getLastCustomerId());
        assertEquals("Hotelkunde", profile.getLastCustomerName());
        assertTrue(profile.getCompanyFeatureKeys().contains("projects"));
        assertEquals(List.of("ROLE_ADMIN"), profile.getRoles());
        assertEquals(permissions, profile.getPagePermissions());
        verify(userRepository).findByUsernameWithProfileContext("john");
        verify(userPermissionService).resolvePagePermissions(existingUser);
    }

    @Test
    void getUserProfileByUsername_rejectsDeletedUsers() {
        existingUser.setDeleted(true);
        when(userRepository.findByUsernameWithProfileContext("john"))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserProfileByUsername("john")
        );
        verifyNoInteractions(userPermissionService);
    }

    @Test
    void updateUser_updatesExistingUser() {
        User updated = new User();
        updated.setUsername("john");
        updated.setFirstName("Jane");
        updated.setLastName("Smith");
        updated.setEmail("jane@example.com");
        updated.setEmailNotifications(false);
        updated.setAnnualVacationDays(15);
        updated.setIsHourly(false);
        updated.setIsPercentage(true);
        updated.setWorkPercentage(80);
        updated.setHourlyRate(25.0);
        updated.setMonthlySalary(3500.0);
        updated.setTrackingBalanceInMinutes(null);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(updated);

        assertEquals("Jane", result.getFirstName());
        assertEquals("Smith", result.getLastName());
        assertEquals("jane@example.com", result.getEmail());
        assertFalse(result.isEmailNotifications());
        assertEquals(15, result.getAnnualVacationDays());
        assertFalse(result.getIsHourly());
        assertTrue(result.getIsPercentage());
        assertEquals(80, result.getWorkPercentage());
        assertEquals(25.0, result.getHourlyRate());
        assertEquals(3500.0, result.getMonthlySalary());
        assertEquals(0, result.getTrackingBalanceInMinutes());

        verify(userRepository).findByUsername("john");
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUser_throwsException_whenUserNotFound() {
        User updated = new User();
        updated.setUsername("unknown");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.updateUser(updated));
        verify(userRepository).findByUsername("unknown");
        verify(userRepository, never()).save(any());
    }
}

