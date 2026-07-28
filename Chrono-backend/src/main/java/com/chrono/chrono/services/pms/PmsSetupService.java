package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsSetupResponse;
import com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest;
import com.chrono.chrono.dto.pms.UpsertRoomRequest;
import com.chrono.chrono.dto.pms.UpsertRoomTypeRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import com.chrono.chrono.entities.pms.RoomType;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.RoomRepository;
import com.chrono.chrono.repositories.pms.RoomTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class PmsSetupService {

    private final HotelPropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;

    public PmsSetupService(HotelPropertyRepository propertyRepository,
                           RoomTypeRepository roomTypeRepository,
                           RoomRepository roomRepository) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public PmsSetupResponse getSetup(Company company) {
        requireCompany(company);
        List<PmsSetupResponse.PropertyView> properties = propertyRepository
                .findAllByCompany_IdOrderByNameAsc(company.getId())
                .stream()
                .map(this::toPropertyView)
                .toList();

        int totalRoomTypes = properties.stream().mapToInt(property -> property.roomTypes().size()).sum();
        int totalRooms = properties.stream().mapToInt(property -> property.rooms().size()).sum();
        boolean foundationComplete = properties.stream()
                .anyMatch(property -> property.active()
                        && !property.roomTypes().isEmpty()
                        && !property.rooms().isEmpty());

        return new PmsSetupResponse(
                properties,
                properties.size(),
                totalRoomTypes,
                totalRooms,
                foundationComplete
        );
    }

    @Transactional
    public PmsSetupResponse createProperty(Company company, UpsertHotelPropertyRequest request) {
        requireCompany(company);
        String code = normalizeCode(request.code());
        if (propertyRepository.existsByCompany_IdAndCodeIgnoreCase(company.getId(), code)) {
            throw conflict("A hotel with this code already exists.");
        }

        HotelProperty property = new HotelProperty();
        property.setCompany(company);
        applyProperty(property, request, true);
        propertyRepository.save(property);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse updateProperty(Company company,
                                           Long propertyId,
                                           UpsertHotelPropertyRequest request) {
        requireCompany(company);
        HotelProperty property = requireProperty(company.getId(), propertyId);
        String code = normalizeCode(request.code());
        if (propertyRepository.existsByCompany_IdAndCodeIgnoreCaseAndIdNot(company.getId(), code, propertyId)) {
            throw conflict("A hotel with this code already exists.");
        }

        applyProperty(property, request, false);
        propertyRepository.save(property);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse createRoomType(Company company,
                                           Long propertyId,
                                           UpsertRoomTypeRequest request) {
        requireCompany(company);
        HotelProperty property = requireProperty(company.getId(), propertyId);
        validateOccupancy(request);
        String code = normalizeCode(request.code());
        if (roomTypeRepository.existsByProperty_IdAndCodeIgnoreCase(propertyId, code)) {
            throw conflict("A room type with this code already exists.");
        }

        RoomType roomType = new RoomType();
        roomType.setProperty(property);
        applyRoomType(roomType, request, true);
        roomTypeRepository.save(roomType);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse updateRoomType(Company company,
                                           Long roomTypeId,
                                           UpsertRoomTypeRequest request) {
        requireCompany(company);
        validateOccupancy(request);
        RoomType roomType = roomTypeRepository.findByIdAndProperty_Company_Id(roomTypeId, company.getId())
                .orElseThrow(() -> notFound("Room type not found."));
        if (roomTypeRepository.existsByProperty_IdAndCodeIgnoreCaseAndIdNot(
                roomType.getProperty().getId(),
                normalizeCode(request.code()),
                roomTypeId
        )) {
            throw conflict("A room type with this code already exists.");
        }

        applyRoomType(roomType, request, false);
        roomTypeRepository.save(roomType);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse createRoom(Company company,
                                       Long propertyId,
                                       UpsertRoomRequest request) {
        requireCompany(company);
        HotelProperty property = requireProperty(company.getId(), propertyId);
        RoomType roomType = requireRoomTypeForProperty(company.getId(), propertyId, request.roomTypeId());
        String roomNumber = cleanRequired(request.number());
        if (roomRepository.existsByProperty_IdAndNumberIgnoreCase(propertyId, roomNumber)) {
            throw conflict("A room with this number already exists.");
        }

        Room room = new Room();
        room.setProperty(property);
        room.setRoomType(roomType);
        applyRoom(room, request, true);
        roomRepository.save(room);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse updateRoom(Company company,
                                       Long roomId,
                                       UpsertRoomRequest request) {
        requireCompany(company);
        Room room = roomRepository.findByIdAndProperty_Company_Id(roomId, company.getId())
                .orElseThrow(() -> notFound("Room not found."));
        Long propertyId = room.getProperty().getId();
        RoomType roomType = requireRoomTypeForProperty(company.getId(), propertyId, request.roomTypeId());
        if (roomRepository.existsByProperty_IdAndNumberIgnoreCaseAndIdNot(
                propertyId,
                cleanRequired(request.number()),
                roomId
        )) {
            throw conflict("A room with this number already exists.");
        }

        room.setRoomType(roomType);
        applyRoom(room, request, false);
        roomRepository.save(room);
        return getSetup(company);
    }

    private PmsSetupResponse.PropertyView toPropertyView(HotelProperty property) {
        List<RoomType> roomTypes = roomTypeRepository
                .findAllByProperty_IdOrderBySortOrderAscNameAsc(property.getId());
        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(property.getId());

        List<PmsSetupResponse.RoomTypeView> roomTypeViews = roomTypes.stream()
                .map(roomType -> new PmsSetupResponse.RoomTypeView(
                        roomType.getId(),
                        property.getId(),
                        roomType.getCode(),
                        roomType.getName(),
                        roomType.getDescription(),
                        roomType.getBaseOccupancy(),
                        roomType.getMaxOccupancy(),
                        roomType.getBedCount(),
                        roomType.getBedType(),
                        roomType.getSortOrder(),
                        roomType.isActive(),
                        rooms.stream().filter(room -> room.getRoomType().getId().equals(roomType.getId())).count()
                ))
                .toList();

        List<PmsSetupResponse.RoomView> roomViews = rooms.stream()
                .map(room -> new PmsSetupResponse.RoomView(
                        room.getId(),
                        property.getId(),
                        room.getRoomType().getId(),
                        room.getRoomType().getCode(),
                        room.getRoomType().getName(),
                        room.getNumber(),
                        room.getName(),
                        room.getFloor(),
                        room.getHousekeepingSection(),
                        room.getOperationalStatus(),
                        room.getHousekeepingStatus(),
                        room.isActive()
                ))
                .toList();

        return new PmsSetupResponse.PropertyView(
                property.getId(),
                property.getCode(),
                property.getName(),
                property.getLegalName(),
                property.getCountryCode(),
                property.getCurrencyCode(),
                property.getTimezone(),
                property.getAddressLine1(),
                property.getPostalCode(),
                property.getCity(),
                property.getPhone(),
                property.getEmail(),
                property.getCheckInTime(),
                property.getCheckOutTime(),
                property.isActive(),
                roomTypeViews,
                roomViews
        );
    }

    private HotelProperty requireProperty(Long companyId, Long propertyId) {
        return propertyRepository.findByIdAndCompany_Id(propertyId, companyId)
                .orElseThrow(() -> notFound("Hotel not found."));
    }

    private RoomType requireRoomTypeForProperty(Long companyId, Long propertyId, Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findByIdAndProperty_Company_Id(roomTypeId, companyId)
                .orElseThrow(() -> notFound("Room type not found."));
        if (!roomType.getProperty().getId().equals(propertyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room type belongs to another hotel.");
        }
        return roomType;
    }

    private void applyProperty(HotelProperty property,
                               UpsertHotelPropertyRequest request,
                               boolean creating) {
        validateTimezoneAndCurrency(request.timezone(), request.currencyCode());
        property.setCode(normalizeCode(request.code()));
        property.setName(cleanRequired(request.name()));
        property.setLegalName(cleanNullable(request.legalName()));
        property.setCountryCode(request.countryCode().trim().toUpperCase(Locale.ROOT));
        property.setCurrencyCode(request.currencyCode().trim().toUpperCase(Locale.ROOT));
        property.setTimezone(request.timezone().trim());
        property.setAddressLine1(cleanNullable(request.addressLine1()));
        property.setPostalCode(cleanNullable(request.postalCode()));
        property.setCity(cleanNullable(request.city()));
        property.setPhone(cleanNullable(request.phone()));
        property.setEmail(normalizeEmail(request.email()));
        property.setCheckInTime(request.checkInTime());
        property.setCheckOutTime(request.checkOutTime());
        if (request.active() != null) {
            property.setActive(request.active());
        } else if (creating) {
            property.setActive(true);
        }
    }

    private void applyRoomType(RoomType roomType,
                               UpsertRoomTypeRequest request,
                               boolean creating) {
        roomType.setCode(normalizeCode(request.code()));
        roomType.setName(cleanRequired(request.name()));
        roomType.setDescription(cleanNullable(request.description()));
        roomType.setBaseOccupancy(request.baseOccupancy());
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setBedCount(request.bedCount());
        roomType.setBedType(cleanNullable(request.bedType()));
        roomType.setSortOrder(request.sortOrder());
        if (request.active() != null) {
            roomType.setActive(request.active());
        } else if (creating) {
            roomType.setActive(true);
        }
    }

    private void applyRoom(Room room, UpsertRoomRequest request, boolean creating) {
        room.setNumber(cleanRequired(request.number()));
        room.setName(cleanNullable(request.name()));
        room.setFloor(cleanNullable(request.floor()));
        room.setHousekeepingSection(cleanNullable(request.housekeepingSection()));
        if (request.operationalStatus() != null) {
            room.setOperationalStatus(request.operationalStatus());
        } else if (creating) {
            room.setOperationalStatus(RoomOperationalStatus.IN_SERVICE);
        }
        if (request.active() != null) {
            room.setActive(request.active());
        } else if (creating) {
            room.setActive(true);
        }
    }

    private void validateOccupancy(UpsertRoomTypeRequest request) {
        if (request.maxOccupancy() < request.baseOccupancy()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum occupancy must not be lower than base occupancy."
            );
        }
    }

    private void validateTimezoneAndCurrency(String timezone, String currencyCode) {
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown timezone.");
        }
        try {
            Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency.");
        }
    }

    private void requireCompany(Company company) {
        if (company == null || company.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A company assignment is required.");
        }
    }

    private String normalizeCode(String value) {
        return cleanRequired(value).toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String normalizeEmail(String value) {
        String cleaned = cleanNullable(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String cleanRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
