package com.chrono.chrono.services;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        AttemptWindow attemptWindow = attempts.get(key);
        if (attemptWindow == null) {
            return false;
        }

        synchronized (attemptWindow) {
            attemptWindow.pruneExpiredFailures();
            if (attemptWindow.blockedUntil != null && attemptWindow.blockedUntil.isAfter(Instant.now())) {
                return true;
            }

            if (attemptWindow.failures.isEmpty()) {
                attempts.remove(key, attemptWindow);
            }
            return false;
        }
    }

    public void recordFailure(String key) {
        AttemptWindow attemptWindow = attempts.computeIfAbsent(key, ignored -> new AttemptWindow());
        synchronized (attemptWindow) {
            attemptWindow.pruneExpiredFailures();
            attemptWindow.failures.addLast(Instant.now());
            if (attemptWindow.failures.size() >= MAX_FAILURES) {
                attemptWindow.blockedUntil = Instant.now().plus(BLOCK_DURATION);
            }
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private static final class AttemptWindow {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;

        private void pruneExpiredFailures() {
            Instant cutoff = Instant.now().minus(FAILURE_WINDOW);
            while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
                failures.removeFirst();
            }
            if (blockedUntil != null && !blockedUntil.isAfter(Instant.now())) {
                blockedUntil = null;
            }
        }
    }
}
