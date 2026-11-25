package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.FolioItemType;
import com.chrono.chrono.dto.pms.CreateGroupBookingRequest.RoomingEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class PmsGroupOperationsDto {
    private PmsGroupOperationsDto() {}
    public record Allotment(@NotNull Long roomTypeId,@NotNull LocalDate startDate,@NotNull LocalDate endDate,
                            @Min(1) @Max(10000) int quantity,@NotNull LocalDate releaseDate) {}
    public record RoomingList(@NotEmpty @Size(max=1000) List<@Valid RoomingEntry> rooms) {}
    public record Routing(@NotNull Set<FolioItemType> types) {}
    public enum Action { CHECK_IN, CHECK_OUT }
    public record BulkOperation(@NotNull Action action,@NotEmpty @Size(max=1000) List<@NotNull Long> reservationIds,LocalDate businessDate) {}
    public record MemberResult(Long reservationId,boolean success,String message) {}
    public record Night(LocalDate date,long pickedUp,long held) {}
    public record AllotmentView(Long id,Long roomTypeId,String roomTypeName,LocalDate startDate,LocalDate endDate,
                                int quantity,LocalDate releaseDate,boolean released,List<Night> nights) {}
    public record View(Long groupId,Long masterFolioId,Set<FolioItemType> routedTypes,List<AllotmentView> allotments) {}
}
