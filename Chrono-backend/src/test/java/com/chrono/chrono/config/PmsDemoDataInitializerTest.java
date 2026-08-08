package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import com.chrono.chrono.services.pms.PmsSetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsDemoDataInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HotelPropertyRepository propertyRepository;
    @Mock
    private PmsSetupService setupService;
    @Mock
    private PmsOperationsService operationsService;
    @Mock
    private PmsAdvancedService advancedService;

    private PmsDemoDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new PmsDemoDataInitializer(
                userRepository,
                propertyRepository,
                setupService,
                operationsService,
                advancedService,
                "Christopher"
        );
    }

    @Test
    void skipsSeedingWhenTheLocalTestAccountDoesNotExist() {
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.empty());

        initializer.run();

        verifyNoInteractions(propertyRepository, setupService, operationsService, advancedService);
    }

    @Test
    void skipsSeedingWhenTheDemoHotelAlreadyExists() {
        Company company = new Company("Chrono PMS Test");
        company.setId(17L);
        User user = new User();
        user.setUsername("Christopher");
        user.setCompany(company);
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(user));
        when(propertyRepository.existsByCompany_IdAndCodeIgnoreCase(
                17L, PmsDemoDataInitializer.DEMO_PROPERTY_CODE)).thenReturn(true);

        initializer.run();

        verify(propertyRepository).existsByCompany_IdAndCodeIgnoreCase(
                17L, PmsDemoDataInitializer.DEMO_PROPERTY_CODE);
        verifyNoInteractions(setupService, operationsService, advancedService);
    }

    @Test
    void isRestrictedToLocalProfileAndRunsAfterTheTestAccountInitializer() {
        assertThat(PmsDemoDataInitializer.class.getAnnotation(Profile.class).value())
                .containsExactly("local");
        assertThat(PmsDemoDataInitializer.class.getAnnotation(Order.class).value())
                .isGreaterThan(PmsTestAccountInitializer.class.getAnnotation(Order.class).value());

        ConditionalOnProperty condition =
                PmsDemoDataInitializer.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.name()).containsExactly("app.pms.demo-data.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }
}
