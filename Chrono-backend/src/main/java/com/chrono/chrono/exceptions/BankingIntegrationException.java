package com.chrono.chrono.exceptions;

/**
 * Signals that a downstream banking integration failed or is not configured correctly.
 * This exception is treated as a business error so that controllers can return
 * a meaningful response to the client instead of masking it as an internal server error.
 */
public class BankingIntegrationException extends RuntimeException {

    public BankingIntegrationException(String message) {
        super(message);
    }

    public BankingIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
