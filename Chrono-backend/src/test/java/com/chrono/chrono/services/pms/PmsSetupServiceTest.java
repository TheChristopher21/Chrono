package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest;
import com.chrono.chrono.dto.pms.UpsertRoomTypeRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.RoomRepository;
import com.chrono.chrono.repositories.pms.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsSetupServiceTest {

    @Mock
    private HotelPropertyRepository propertyRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomRepository roomRepository;

    private PmsSetupService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new PmsSetupService(propertyRepository, roomTypeRepository, roomRepository);
        company = new Company("Chrono Hotel AG");
        company.setId(12L);
    }

    @Test
    void createsCompanyScopedPropertyWithNormalizedMasterData() {
        when(propertyRepository.existsByCompany_IdAndCodeIgnoreCase(12L, "BASEL-CITY")).thenReturn(false);
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of());

        service.createProperty(company, propertyRequest(" basel city ", " Hotel Central "));

        ArgumentCaptor<HotelProperty> captor = ArgumentCaptor.forClass(HotelProperty.class);
        verify(propertyRepository).save(captor.capture());
        HotelProperty saved = captor.getValue();
        assertThat(saved.getCompany()).isSameAs(company);
        assertThat(saved.getCode()).isEqualTo("BASEL-CITY");
        assertThat(saved.getName()).isEqualTo("Hotel Central");
        assertThat(saved.getCurrencyCode()).isEqualTo("CHF");
        assertThat(saved.getTimezone()).isEqualTo("Europe/Zurich");
    }

    @Test
    void reportsDuplicateHotelCodeWithReadableGermanMessage() {
        when(propertyRepository.existsByCompany_IdAndCodeIgnoreCase(12L, "BASEL-CITY"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createProperty(
                company,
                propertyRequest("basel city", "Hotel Central")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Code bereits vergeben");
    }

    @Test
    void rejectsRoomTypeWhenMaximumOccupancyIsLowerThanBaseOccupancy() {
        HotelProperty property = new HotelProperty();
        property.setCompany(company);
        when(propertyRepository.findByIdAndCompany_Id(5L, 12L)).thenReturn(Optional.of(property));

        UpsertRoomTypeRequest request = new UpsertRoomTypeRequest(
                "DBL",
                "Doppelzimmer",
                null,
                3,
                2,
                1,
                "Doppelbett",
                0,
                true
        );

        assertThatThrownBy(() -> service.createRoomType(company, 5L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maximale Belegung");
    }

    @Test
    void returnsAnEmptyButTruthfulSetupForACompanyWithoutHotels() {
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of());

        var response = service.getSetup(company);

        assertThat(response.properties()).isEmpty();
        assertThat(response.totalProperties()).isZero();
        assertThat(response.totalRoomTypes()).isZero();
        assertThat(response.totalRooms()).isZero();
        assertThat(response.foundationComplete()).isFalse();
    }

    private UpsertHotelPropertyRequest propertyRequest(String code, String name) {
        return new UpsertHotelPropertyRequest(
                code,
                name,
                "Chrono Hotel AG",
                "CH",
                "CHF",
                "Europe/Zurich",
                "Bahnhofstrasse 1",
                "8001",
                "Zürich",
                null,
                "hotel@example.com",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0),
                true
        );
    }
}
