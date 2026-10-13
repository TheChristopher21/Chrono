package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsPropertyGrant;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PmsPropertyGrantRepository;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import com.chrono.chrono.services.pms.PmsSetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PmsDemoDataInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HotelPropertyRepository propertyRepository;
    @Mock
    private PmsPropertyGrantRepository grantRepository;
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
                grantRepository,
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

        verifyNoInteractions(propertyRepository, grantRepository, setupService, operationsService, advancedService);
    }

    @Test
    void skipsSeedingWhenTheDemoHotelAlreadyExists() {
        Company company = new Company("Chrono PMS Test");
        company.setId(17L);
        User user = new User();
        user.setUsername("Christopher");
        user.setCompany(company);
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(user));
        HotelProperty hotel = new HotelProperty(); hotel.setCompany(company); hotel.setCode("DEMO"); hotel.setName("Other hotel using the same code");
        when(propertyRepository.findByCompany_IdAndCodeIgnoreCase(
                17L, PmsDemoDataInitializer.DEMO_PROPERTY_CODE)).thenReturn(Optional.of(hotel));

        initializer.run();

        verify(propertyRepository).findByCompany_IdAndCodeIgnoreCase(
                17L, PmsDemoDataInitializer.DEMO_PROPERTY_CODE);
        verifyNoInteractions(grantRepository, setupService, operationsService, advancedService);
    }

    @Test void repairsMissingGrantsOnlyForTheGeneratedDemoHotelWithoutCreatingMasterAccess() {
        User user = demoUser(); HotelProperty demo = demoHotel(user.getCompany());
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(user));
        when(propertyRepository.findByCompany_IdAndCodeIgnoreCase(17L, "DEMO")).thenReturn(Optional.of(demo));
        var otherHotel = new HotelProperty(); ReflectionTestUtils.setField(otherHotel, "id", 99L);
        var otherGrant = new PmsPropertyGrant(); otherGrant.setProperty(otherHotel); otherGrant.setPermissions(Map.of("HOUSEKEEPING", "VIEW"));
        when(grantRepository.findByUser_IdAndProperty_Company_Id(7L, 17L)).thenReturn(List.of(otherGrant));
        initializer.run();
        var captured = ArgumentCaptor.forClass(PmsPropertyGrant.class); verify(grantRepository).save(captured.capture());
        assertThat(captured.getValue().getUser()).isSameAs(user);
        assertThat(captured.getValue().getProperty()).isSameAs(demo);
        assertThat(captured.getValue().getPermissions().keySet()).containsExactlyInAnyOrderElementsOf(PmsPropertyAccessService.PERMISSIONS);
        assertThat(captured.getValue().getPermissions()).allSatisfy((key, value) -> assertThat(value).isEqualTo("MANAGE"));
        assertThat(otherGrant.getPermissions()).containsExactlyEntriesOf(Map.of("HOUSEKEEPING", "VIEW"));
        assertThat(user.getPagePermissions()).containsExactlyEntriesOf(Map.of("pms", "MANAGE"));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(setupService, operationsService, advancedService);
    }

    @Test void preservesExplicitExistingDemoGrantInsteadOfResettingItsPermissions() {
        User user = demoUser(); HotelProperty demo = demoHotel(user.getCompany());
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(user));
        when(propertyRepository.findByCompany_IdAndCodeIgnoreCase(17L, "DEMO")).thenReturn(Optional.of(demo));
        var grant = new PmsPropertyGrant(); grant.setUser(user); grant.setProperty(demo); grant.setPermissions(Map.of("FRONT_DESK", "VIEW"));
        when(grantRepository.findByUser_IdAndProperty_Company_Id(7L, 17L)).thenReturn(List.of(grant));
        initializer.run(); initializer.run();
        verify(grantRepository, never()).save(any());
        assertThat(grant.getPermissions()).containsExactlyEntriesOf(Map.of("FRONT_DESK", "VIEW"));
        verifyNoInteractions(setupService, operationsService, advancedService);
    }

    @Test void doesNotGrantAccessWhenGeneratedIdentityBelongsToAnotherCompany() {
        User user = demoUser(); HotelProperty demo = demoHotel(new Company("Other company")); demo.getCompany().setId(18L);
        when(userRepository.findByUsername("Christopher")).thenReturn(Optional.of(user));
        when(propertyRepository.findByCompany_IdAndCodeIgnoreCase(17L, "DEMO")).thenReturn(Optional.of(demo));
        initializer.run();
        verifyNoInteractions(grantRepository, setupService, operationsService, advancedService);
    }

    private User demoUser() {
        Company company = new Company("Chrono PMS Test"); company.setId(17L);
        User user = new User(); user.setId(7L); user.setUsername("Christopher"); user.setCompany(company); user.setPagePermissions(Map.of("pms", "MANAGE"));
        return user;
    }
    private HotelProperty demoHotel(Company company) {
        HotelProperty hotel = new HotelProperty(); ReflectionTestUtils.setField(hotel, "id", 5L); hotel.setCompany(company);
        hotel.setCode(PmsDemoDataInitializer.DEMO_PROPERTY_CODE); hotel.setName(PmsDemoDataInitializer.DEMO_PROPERTY_NAME);
        hotel.setLegalName(PmsDemoDataInitializer.DEMO_LEGAL_NAME); hotel.setEmail(PmsDemoDataInitializer.DEMO_EMAIL);
        return hotel;
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
