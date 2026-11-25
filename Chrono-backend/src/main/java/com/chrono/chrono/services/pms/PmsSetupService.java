package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsSetupResponse;
import com.chrono.chrono.dto.pms.BulkCreateRoomsRequest;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PmsSetupService {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

    private final HotelPropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private org.springframework.beans.factory.ObjectProvider<PmsPropertyAccessService> accessProvider;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAccessProvider(org.springframework.beans.factory.ObjectProvider<PmsPropertyAccessService> accessProvider) { this.accessProvider=accessProvider; }

    public PmsSetupService(HotelPropertyRepository propertyRepository,
                           RoomTypeRepository roomTypeRepository,
                           RoomRepository roomRepository) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public PmsSetupResponse getSetup(Company company) {
        return getSetup(company, true);
    }

    @Transactional(readOnly = true)
    public PmsSetupResponse getSetup(Company company, boolean includeRooms) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken))
            return getSetupForUser(company,includeRooms,auth.getName());
        return buildSetup(company,includeRooms,null);
    }

    @Transactional(readOnly=true)
    public PmsSetupResponse getSetupForUser(Company company,boolean includeRooms,String username) {
        requireCompany(company);
        if(accessProvider==null || username==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hotelrechte konnten nicht geprüft werden.");
        var access=accessProvider.getIfAvailable();
        if(access==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hotelrechte konnten nicht geprüft werden.");
        var actor=access.access(username);
        if(!java.util.Objects.equals(actor.companyId(),company.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hotelbetrieb nicht verfügbar.");
        return buildSetup(company,includeRooms,actor);
    }

    private PmsSetupResponse buildSetup(Company company,boolean includeRooms,PmsPropertyAccessService.Access actor) {
        requireCompany(company);
        List<HotelProperty> visible=actor==null || actor.master() ? propertyRepository.findAllByCompany_IdOrderByNameAsc(company.getId())
                : actor.grants().isEmpty() ? List.of() : propertyRepository.findAllByCompany_IdAndIdInOrderByNameAsc(company.getId(),actor.grants().keySet());
        List<PmsSetupResponse.PropertyView> properties = visible
                .stream()
                .map(property -> toPropertyView(property, includeRooms))
                .toList();

        int totalRoomTypes = properties.stream().mapToInt(property -> property.roomTypes().size()).sum();
        int totalRooms = properties.stream().mapToInt(property -> (int) property.roomTypes().stream()
                .mapToLong(PmsSetupResponse.RoomTypeView::roomCount).sum()).sum();
        boolean foundationComplete = properties.stream()
                .anyMatch(property -> property.active()
                        && !property.roomTypes().isEmpty()
                        && property.roomTypes().stream().anyMatch(type -> type.roomCount() > 0));

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
            throw conflict("Für diesen Hotelbetrieb ist der Code bereits vergeben.");
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
        HotelProperty property = propertyRepository.findByIdAndCompany_IdForUpdate(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotelbetrieb nicht gefunden."));
        String code = normalizeCode(request.code());
        if (propertyRepository.existsByCompany_IdAndCodeIgnoreCaseAndIdNot(company.getId(), code, propertyId)) {
            throw conflict("Für diesen Hotelbetrieb ist der Code bereits vergeben.");
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
            throw conflict("Für diesen Hotelbetrieb ist der Zimmertyp-Code bereits vergeben.");
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
                .orElseThrow(() -> notFound("Zimmertyp nicht gefunden."));
        if (roomTypeRepository.existsByProperty_IdAndCodeIgnoreCaseAndIdNot(
                roomType.getProperty().getId(),
                normalizeCode(request.code()),
                roomTypeId
        )) {
            throw conflict("Für diesen Hotelbetrieb ist der Zimmertyp-Code bereits vergeben.");
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
            throw conflict("Für diesen Hotelbetrieb ist die Zimmernummer bereits vergeben.");
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
                .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
        Long propertyId = room.getProperty().getId();
        RoomType roomType = requireRoomTypeForProperty(company.getId(), propertyId, request.roomTypeId());
        if (roomRepository.existsByProperty_IdAndNumberIgnoreCaseAndIdNot(
                propertyId,
                cleanRequired(request.number()),
                roomId
        )) {
            throw conflict("Für diesen Hotelbetrieb ist die Zimmernummer bereits vergeben.");
        }

        room.setRoomType(roomType);
        applyRoom(room, request, false);
        roomRepository.save(room);
        return getSetup(company);
    }

    @Transactional
    public PmsSetupResponse createRooms(Company company,
                                        Long propertyId,
                                        BulkCreateRoomsRequest request) {
        requireCompany(company);
        HotelProperty property = requireProperty(company.getId(), propertyId);
        RoomType roomType = requireRoomTypeForProperty(company.getId(), propertyId, request.roomTypeId());
        List<String> roomNumbers = sequentialRoomNumbers(request.startNumber(), request.count());
        Set<String> normalized = new HashSet<>();
        for (String roomNumber : roomNumbers) {
            if (!normalized.add(roomNumber.toLowerCase(Locale.ROOT))
                    || roomRepository.existsByProperty_IdAndNumberIgnoreCase(propertyId, roomNumber)) {
                throw conflict("Die Zimmernummer " + roomNumber + " ist bereits vergeben.");
            }
        }
        List<Room> rooms = new ArrayList<>();
        for (int index = 0; index < roomNumbers.size(); index++) {
            Room room = new Room();
            room.setProperty(property);
            room.setRoomType(roomType);
            String name = cleanNullable(request.namePrefix());
            UpsertRoomRequest roomRequest = new UpsertRoomRequest(
                    request.roomTypeId(), roomNumbers.get(index),
                    name == null ? null : name + " " + (index + 1),
                    request.floor(), request.housekeepingSection(), request.operationalStatus(),
                    request.active(), request.features());
            applyRoom(room, roomRequest, true);
            rooms.add(room);
        }
        roomRepository.saveAll(rooms);
        return getSetup(company);
    }

    private PmsSetupResponse.PropertyView toPropertyView(HotelProperty property, boolean includeRooms) {
        List<RoomType> roomTypes = roomTypeRepository
                .findAllByProperty_IdOrderBySortOrderAscNameAsc(property.getId());
        List<Room> rooms = includeRooms ? roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(property.getId()) : List.of();
        java.util.Map<Long, Long> roomCounts = includeRooms
                ? rooms.stream().collect(java.util.stream.Collectors.groupingBy(room -> room.getRoomType().getId(), java.util.stream.Collectors.counting()))
                : roomRepository.countRoomsByType(property.getId()).stream().collect(java.util.stream.Collectors.toMap(
                        RoomRepository.RoomTypeCount::getRoomTypeId, RoomRepository.RoomTypeCount::getRoomCount));

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
                        roomCounts.getOrDefault(roomType.getId(), 0L)
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
                        room.getFeatures(),
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
                roomViews,
                property.getTaxNumber(), property.getTaxRegistrationLabel(), property.getRegistrationNumber(),
                property.getAddressLine2(), property.getRegion(), property.getInvoiceFooter(),
                property.getInvoicePrefix(), property.getInvoiceDueDays()
        );
    }

    private HotelProperty requireProperty(Long companyId, Long propertyId) {
        return propertyRepository.findByIdAndCompany_Id(propertyId, companyId)
                .orElseThrow(() -> notFound("Hotelbetrieb nicht gefunden."));
    }

    private RoomType requireRoomTypeForProperty(Long companyId, Long propertyId, Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findByIdAndProperty_Company_Id(roomTypeId, companyId)
                .orElseThrow(() -> notFound("Zimmertyp nicht gefunden."));
        if (!roomType.getProperty().getId().equals(propertyId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Der gewählte Zimmertyp gehört zu einem anderen Hotelbetrieb."
            );
        }
        return roomType;
    }

    private void applyProperty(HotelProperty property,
                               UpsertHotelPropertyRequest request,
                               boolean creating) {
        validateTimezoneAndCurrency(request.timezone(), request.currencyCode());
        PmsMoney.digits(request.currencyCode());
        if (!creating && !property.getCurrencyCode().equalsIgnoreCase(request.currencyCode())
                && propertyRepository.hasCurrencyDependentRecords(property.getId())) {
            throw conflict("Die Hotelwährung kann nach Einrichtung von Raten, Aufenthalten oder Finanzdaten nicht mehr geändert werden.");
        }
        if (!Set.of(Locale.getISOCountries()).contains(request.countryCode().trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Land muss ein gültiger ISO-Ländercode sein.");
        }
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
        if (request.taxNumber() != null) property.setTaxNumber(cleanNullable(request.taxNumber()));
        if (request.taxRegistrationLabel() != null) {
            property.setTaxRegistrationLabel(cleanNullable(request.taxRegistrationLabel()) == null
                    ? "VAT / Tax ID" : request.taxRegistrationLabel().trim());
        }
        if (request.registrationNumber() != null) property.setRegistrationNumber(cleanNullable(request.registrationNumber()));
        if (request.addressLine2() != null) property.setAddressLine2(cleanNullable(request.addressLine2()));
        if (request.region() != null) property.setRegion(cleanNullable(request.region()));
        if (request.invoiceFooter() != null) property.setInvoiceFooter(cleanNullable(request.invoiceFooter()));
        if (request.invoicePrefix() != null) property.setInvoicePrefix(request.invoicePrefix().trim().toUpperCase(Locale.ROOT));
        if (request.invoiceDueDays() != null) property.setInvoiceDueDays(request.invoiceDueDays());
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
        room.setFeatures(cleanNullable(request.features()));
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
                    "Die maximale Belegung darf nicht kleiner als die Standardbelegung sein."
            );
        }
    }

    private List<String> sequentialRoomNumbers(String startNumber, int count) {
        String cleaned = cleanRequired(startNumber);
        if (count <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Anzahl muss mindestens 1 sein.");
        }
        Matcher matcher = TRAILING_NUMBER.matcher(cleaned);
        if (!matcher.matches()) {
            if (count == 1) {
                return List.of(cleaned);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Für mehrere Zimmer muss die Start-Zimmernummer mit einer Zahl enden."
            );
        }
        String prefix = matcher.group(1);
        String digits = matcher.group(2);
        long start;
        try {
            start = Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Start-Zimmernummer ist ungültig.");
        }
        List<String> result = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            result.add(prefix + String.format(Locale.ROOT, "%0" + digits.length() + "d", start + offset));
        }
        return result;
    }

    private void validateTimezoneAndCurrency(String timezone, String currencyCode) {
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Zeitzone ist ungültig.");
        }
        try {
            Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Währungscode ist ungültig.");
        }
    }

    private void requireCompany(Company company) {
        if (company == null || company.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
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
