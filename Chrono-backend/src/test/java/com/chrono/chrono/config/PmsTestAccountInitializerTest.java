package com.chrono.chrono.config;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsTestAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PmsTestAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new PmsTestAccountInitializer(
                userRepository,
                roleRepository,
                companyRepository,
                passwordEncoder,
                new UserPermissionService()
        );
        ReflectionTestUtils.setField(initializer, "username", "Christopher");
        ReflectionTestUtils.setField(initializer, "password", "test-secret");
    }

    @Test
    void doesNothingWhenBootstrapIsDisabled() {
        ReflectionTestUtils.setField(initializer, "enabled", false);

        initializer.run();

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsTestUserWithPmsPermissionAndEncodedPassword() {
        ReflectionTestUtils.setField(initializer, "enabled", true);
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(companyRepository.findAll()).thenReturn(java.util.List.of());
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("test-secret")).thenReturn("encoded-secret");

        initializer.run();

        verify(userRepository).save(any(User.class));
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());

        assertThat(savedUser.getValue().getPassword()).isEqualTo("encoded-secret");
        assertThat(savedUser.getValue().getPagePermissions())
                .containsEntry(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE);
        assertThat(savedUser.getValue().isIncludeInTimeTracking()).isFalse();
        assertThat(savedUser.getValue().getCompany()).isNotNull();
    }

    @Test
    void runsAfterTheGenericPermissionBackfill() {
        assertThat(PmsTestAccountInitializer.class.getAnnotation(Order.class).value()).isGreaterThan(0);
        assertThat(DataInitializer.class.getAnnotation(Order.class).value())
                .isLessThan(PmsTestAccountInitializer.class.getAnnotation(Order.class).value());
    }

    @Test
    void grantsPmsAccessToExistingUserWithoutChangingPassword() {
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "password", "");
        User existingUser = new User();
        existingUser.setId(21L);
        existingUser.setUsername("Christopher");
        existingUser.setPassword("existing-password-hash");
        existingUser.setCompany(new Company("Existing Company"));
        existingUser.getRoles().add(new Role("ROLE_ADMIN"));
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(existingUser));

        initializer.run();

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository).save(existingUser);
        assertThat(existingUser.getPassword()).isEqualTo("existing-password-hash");
        assertThat(existingUser.getPagePermissions())
                .containsEntry(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE);
    }
}
