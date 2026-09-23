package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.HousekeepingStatus;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import java.time.LocalDate;
import java.util.List;

public record PmsRoomPlanFilter(LocalDate from, int days, int page, int size, String search,
                               Long roomTypeId, String floor, String bedType, String housekeepingSection,
                               HousekeepingStatus housekeepingStatus, RoomOperationalStatus operationalStatus,
                               Integer guests, List<String> features, boolean onlyAvailable, boolean includeInactive) { }
