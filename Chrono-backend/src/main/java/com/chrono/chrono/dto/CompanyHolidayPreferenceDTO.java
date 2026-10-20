package com.chrono.chrono.dto;

public class CompanyHolidayPreferenceDTO {
    private String holidayCode;
    private boolean halfDay;

    public CompanyHolidayPreferenceDTO() {}

    public CompanyHolidayPreferenceDTO(String holidayCode, boolean halfDay) {
        this.holidayCode = holidayCode;
        this.halfDay = halfDay;
    }

    public String getHolidayCode() { return holidayCode; }
    public void setHolidayCode(String holidayCode) { this.holidayCode = holidayCode; }

    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
}
