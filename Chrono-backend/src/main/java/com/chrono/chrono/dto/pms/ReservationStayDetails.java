package com.chrono.chrono.dto.pms;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public record ReservationStayDetails(Long reservationId, List<RoomSegment> roomSegments, List<CoGuest> coGuests) {
    public record RoomSegment(Long id, Long roomId, String roomNumber, Long roomTypeId, String roomTypeName, Long ratePlanId,
                              LocalDate startDate, LocalDate endDate, String reason) {}
    public record CoGuest(Long id, Long guestId, String guestName, LocalDate arrivalDate, LocalDate departureDate, boolean child,
                          LocalDateTime registrationCompletedAt) {}
}
