package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PmsRoomPlanResponse(
        Long propertyId, String timezone, LocalDate today, LocalDate from, LocalDate to,
        int page, int size, long totalRooms, List<RoomView> rooms,
        List<ReservationView> reservations, List<BlockView> blocks, Filters filters) {
    public record RoomView(Long id, String number, String name, String floor,
                           String housekeepingSection, Long roomTypeId, String roomTypeName,
                           String bedType, int maxOccupancy, String features,
                           RoomOperationalStatus operationalStatus, HousekeepingStatus housekeepingStatus,
                           boolean active) { }
    public record ReservationView(Long id, long version, String confirmationCode, Long guestId,
                                  String guestName, Long roomId, Long roomTypeId, Long ratePlanId,
                                  LocalDate arrivalDate, LocalDate departureDate, int adults, int children,
                                  List<Integer> childAges, ReservationStatus status, ReservationSource source,
                                  ReservationGuaranteeStatus guaranteeStatus, LocalDateTime holdUntil,
                                  String notes, String guestPreferenceSnapshot) { }
    public record BlockView(Long id, Long roomId, RoomBlockType type, LocalDate startDate,
                            LocalDate endDate, String reason) { }
    public record TypeOption(Long id, String name) { }
    public record Filters(List<TypeOption> roomTypes, List<String> floors, List<String> bedTypes,
                          List<String> housekeepingSections, List<String> features) { }
}
