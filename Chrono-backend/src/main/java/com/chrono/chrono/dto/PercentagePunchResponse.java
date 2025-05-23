package com.chrono.chrono.dto;

import lombok.Getter;

@Getter
public class PercentagePunchResponse {
    // GETTER
    private final String username;
    private final int worked;
    private final int expected;
    private final int difference;
    private final String message;

    public PercentagePunchResponse(String username, int worked, int expected, int difference, String message) {
        this.username = username;
        this.worked = worked;
        this.expected = expected;
        this.difference = difference;
        this.message = message;
    }

}
