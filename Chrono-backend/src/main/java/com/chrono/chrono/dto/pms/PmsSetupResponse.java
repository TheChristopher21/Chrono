package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import com.chrono.chrono.entities.pms.HousekeepingStatus;

import java.time.LocalTime;
import java.util.List;

public record PmsSetupResponse(
        List<PropertyView> properties,
        int totalProperties,
        int totalRoomTypes,
        int totalRooms,
        boolean foundationComplete
) {
    public record PropertyView(
            Long id,
            String code,
            String name,
            String legalName,
            String countryCode,
            String currencyCode,
            String timezone,
            String addressLine1,
            String postalCode,
            String city,
            String phone,
            String email,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            boolean active,
            List<RoomTypeView> roomTypes,
            List<RoomView> rooms
    ) {
    }

    public record RoomTypeView(
            Long id,
            Long propertyId,
            String code,
            String name,
            String description,
            int baseOccupancy,
            int maxOccupancy,
            int bedCount,
            String bedType,
            int sortOrder,
            boolean active,
            long roomCount
    ) {
    }

    public record RoomView(
            Long id,
            Long propertyId,
            Long roomTypeId,
            String roomTypeCode,
            String roomTypeName,
            String number,
            String name,
            String floor,
            String housekeepingSection,
            RoomOperationalStatus operationalStatus,
            HousekeepingStatus housekeepingStatus,
            boolean active
    ) {
    }
}
