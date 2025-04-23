package com.chrono.chrono.dto;

public class PercentagePunchResponse {
    private String username;
    private int workedMinutes;
    private int expectedMinutes;
    private int differenceMinutes;
    private String message;

    public PercentagePunchResponse(String username,
                                   int workedMinutes,
                                   int expectedMinutes,
                                   int differenceMinutes,
                                   String message) {
        this.username = username;
        this.workedMinutes = workedMinutes;
        this.expectedMinutes = expectedMinutes;
        this.differenceMinutes = differenceMinutes;
        this.message = message;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public int getWorkedMinutes() {
        return workedMinutes;
    }
    public void setWorkedMinutes(int workedMinutes) {
        this.workedMinutes = workedMinutes;
    }

    public int getExpectedMinutes() {
        return expectedMinutes;
    }
    public void setExpectedMinutes(int expectedMinutes) {
        this.expectedMinutes = expectedMinutes;
    }

    public int getDifferenceMinutes() {
        return differenceMinutes;
    }
    public void setDifferenceMinutes(int differenceMinutes) {
        this.differenceMinutes = differenceMinutes;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
