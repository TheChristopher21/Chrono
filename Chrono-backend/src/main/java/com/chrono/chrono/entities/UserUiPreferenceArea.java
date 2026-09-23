package com.chrono.chrono.entities;

import java.util.Locale;

public enum UserUiPreferenceArea {
    APP_TABS,
    TIME_USER_DASHBOARD,
    TIME_ADMIN_DASHBOARD,
    PMS_DASHBOARD;

    public static UserUiPreferenceArea fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Preference area is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown preference area: " + value, exception);
        }
    }
}
