package com.chrono.chrono.services.pms;

/**
 * Provider adapters implement this interface. The stable event id is the
 * idempotency key a remote system should use when it supports one.
 */
public interface PmsOutboxTransport {
    boolean supports(PmsOutboxMessage message);

    void deliver(PmsOutboxMessage message) throws Exception;
}
