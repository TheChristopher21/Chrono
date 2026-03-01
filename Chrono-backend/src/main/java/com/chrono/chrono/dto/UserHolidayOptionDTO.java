package com.chrono.chrono.dto;

import com.chrono.chrono.entities.UserHolidayOption;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UserHolidayOptionDTO {
    private Long id;
    private Long userId;
    private String username;
    private LocalDate holidayDate;
    private UserHolidayOption.HolidayHandlingOption holidayHandlingOption;

    public UserHolidayOptionDTO() {
    }

    public UserHolidayOptionDTO(Long id, Long userId, String username, LocalDate holidayDate, UserHolidayOption.HolidayHandlingOption holidayHandlingOption) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.holidayDate = holidayDate;
        this.holidayHandlingOption = holidayHandlingOption;
    }

    public static UserHolidayOptionDTO fromEntity(UserHolidayOption entity) {
        return new UserHolidayOptionDTO(
                entity.getId(),
                entity.getUser().getId(),
                entity.getUser().getUsername(),
                entity.getHolidayDate(),
                entity.getHolidayHandlingOption()
        );
    }
}