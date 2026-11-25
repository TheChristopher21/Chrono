package com.chrono.chrono.exceptions;

public class UiPreferenceRevisionConflictException extends RuntimeException {

    public UiPreferenceRevisionConflictException(String message) {
        super(message);
    }

    public UiPreferenceRevisionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
