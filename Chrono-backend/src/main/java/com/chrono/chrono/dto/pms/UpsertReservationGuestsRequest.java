package com.chrono.chrono.dto.pms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record UpsertReservationGuestsRequest(@NotNull @Size(max = 39) List<@Valid GuestEntry> guests) {
    public record GuestEntry(@NotNull Long guestId, @NotNull LocalDate arrivalDate, @NotNull LocalDate departureDate, boolean child) {}
}
