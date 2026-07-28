package com.chrono.chrono.config;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserPermissionService userPermissionService;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer();
        ReflectionTestUtils.setField(initializer, "userRepository", userRepository);
        ReflectionTestUtils.setField(initializer, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(initializer, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(initializer, "userPermissionService", userPermissionService);
        ReflectionTestUtils.setField(initializer, "initializeAdmin", true);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "local-secret");
    }

    @Test
    void createsAdminWithAllMandatoryUserFields() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("local-secret")).thenReturn("encoded-secret");
        when(roleRepository.findByRoleName("ROLE_ADMIN"))
                .thenReturn(Optional.of(new Role("ROLE_ADMIN")));
        when(roleRepository.findByRoleName("ROLE_PAYROLL_ADMIN"))
                .thenReturn(Optional.of(new Role("ROLE_PAYROLL_ADMIN")));
        when(userRepository.findAllWithPermissionContext()).thenReturn(List.of());

        initializer.run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getCountry()).isEqualTo("CH");
        assertThat(savedUser.getValue().getPersonnelNumber()).isEqualTo("ADMIN");
        assertThat(savedUser.getValue().getPassword()).isEqualTo("encoded-secret");
        assertThat(savedUser.getValue().getRoles())
                .extracting(Role::getRoleName)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_PAYROLL_ADMIN");
    }
}
