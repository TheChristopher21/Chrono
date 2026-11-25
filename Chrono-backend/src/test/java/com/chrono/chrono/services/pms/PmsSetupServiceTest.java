package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest;
import com.chrono.chrono.dto.pms.BulkCreateRoomsRequest;
import com.chrono.chrono.dto.pms.UpsertRoomTypeRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import com.chrono.chrono.entities.pms.RoomType;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;

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

    @Test
    void compactSetupCountsLargeHotelsWithoutLoadingRoomEntities() {
        HotelProperty property = new HotelProperty();
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        property.setName("Grand Hotel Tokyo");
        property.setCountryCode("JP");
        property.setCurrencyCode("JPY");
        property.setTimezone("Asia/Tokyo");
        property.setRegion("Tokyo");
        property.setTaxNumber("T1234567890123");
        RoomType type = new RoomType();
        ReflectionTestUtils.setField(type, "id", 7L);
        type.setProperty(property);
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of(property));
        when(roomTypeRepository.findAllByProperty_IdOrderBySortOrderAscNameAsc(5L)).thenReturn(List.of(type));
        when(roomRepository.countRoomsByType(5L)).thenReturn(List.of(new RoomRepository.RoomTypeCount() {
            public Long getRoomTypeId() { return 7L; }
            public long getRoomCount() { return 12_000L; }
        }));

        var result = service.getSetup(company, false);

        assertThat(result.totalRooms()).isEqualTo(12_000);
        assertThat(result.totalRoomTypes()).isEqualTo(1);
        assertThat(result.foundationComplete()).isTrue();
        assertThat(result.properties().get(0).rooms()).isEmpty();
        assertThat(result.properties().get(0).roomTypes().get(0).roomCount()).isEqualTo(12_000);
        assertThat(result.properties().get(0).countryCode()).isEqualTo("JP");
        assertThat(result.properties().get(0).currencyCode()).isEqualTo("JPY");
        assertThat(result.properties().get(0).timezone()).isEqualTo("Asia/Tokyo");
        assertThat(result.properties().get(0).taxNumber()).isEqualTo("T1234567890123");
        assertThat(result.properties().get(0).region()).isEqualTo("Tokyo");
        verify(roomRepository, never()).findAllByProperty_IdOrderByFloorAscNumberAsc(anyLong());
    }

    @Test
    void compactSetupWithoutRoomsDoesNotClaimFoundationIsComplete() {
        HotelProperty property = new HotelProperty();
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        RoomType type = new RoomType();
        ReflectionTestUtils.setField(type, "id", 7L);
        type.setProperty(property);
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of(property));
        when(roomTypeRepository.findAllByProperty_IdOrderBySortOrderAscNameAsc(5L)).thenReturn(List.of(type));
        when(roomRepository.countRoomsByType(5L)).thenReturn(List.of());

        var result = service.getSetup(company, false);
        assertThat(result.totalRooms()).isZero();
        assertThat(result.foundationComplete()).isFalse();
        verify(roomRepository, never()).findAllByProperty_IdOrderByFloorAscNumberAsc(anyLong());
    }

    @Test
    void savesInternationalPropertyAndItsInvoiceIdentity() {
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of());
        service.createProperty(company, new UpsertHotelPropertyRequest(
                "NYC", "Grand Hotel", "Grand Hotel LLC", "US", "USD", "America/New_York", "5 Fifth Avenue",
                "10001", "New York", "+12120000000", "hotel@example.com", LocalTime.of(15, 0), LocalTime.of(11, 0), true,
                "12-3456789", "EIN", "NY12345", "Suite 10", "New York", "Thank you for your stay.", "NYC-INV", 14));
        ArgumentCaptor<HotelProperty> captor = ArgumentCaptor.forClass(HotelProperty.class);
        verify(propertyRepository).save(captor.capture());
        HotelProperty saved = captor.getValue();
        assertThat(saved.getCountryCode()).isEqualTo("US");
        assertThat(saved.getCurrencyCode()).isEqualTo("USD");
        assertThat(saved.getTimezone()).isEqualTo("America/New_York");
        assertThat(saved.getTaxNumber()).isEqualTo("12-3456789");
        assertThat(saved.getTaxRegistrationLabel()).isEqualTo("EIN");
        assertThat(saved.getRegistrationNumber()).isEqualTo("NY12345");
        assertThat(saved.getAddressLine2()).isEqualTo("Suite 10");
        assertThat(saved.getRegion()).isEqualTo("New York");
        assertThat(saved.getInvoiceFooter()).isEqualTo("Thank you for your stay.");
        assertThat(saved.getInvoicePrefix()).isEqualTo("NYC-INV");
        assertThat(saved.getInvoiceDueDays()).isEqualTo(14);
    }

    @Test
    void createsSequentialRoomsWithSharedFeaturesInOneOperation() {
        HotelProperty property = new HotelProperty();
        ReflectionTestUtils.setField(property, "id", 5L);
        property.setCompany(company);
        RoomType roomType = new RoomType();
        ReflectionTestUtils.setField(roomType, "id", 7L);
        roomType.setProperty(property);
        when(propertyRepository.findByIdAndCompany_Id(5L, 12L)).thenReturn(Optional.of(property));
        when(roomTypeRepository.findByIdAndProperty_Company_Id(7L, 12L)).thenReturn(Optional.of(roomType));
        when(propertyRepository.findAllByCompany_IdOrderByNameAsc(12L)).thenReturn(List.of());

        service.createRooms(company, 5L, new BulkCreateRoomsRequest(
                7L, "A008", 3, "Gartenzimmer", "1", "Nord",
                "Parkett, ruhig", RoomOperationalStatus.IN_SERVICE, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Room>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Room::getNumber).containsExactly("A008", "A009", "A010");
        assertThat(captor.getValue()).allSatisfy(saved -> {
            assertThat(saved.getFeatures()).isEqualTo("Parkett, ruhig");
            assertThat(saved.getRoomType()).isSameAs(roomType);
        });
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
