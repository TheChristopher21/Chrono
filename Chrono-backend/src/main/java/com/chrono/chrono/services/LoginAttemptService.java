package com.chrono.chrono.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_KEY_LENGTH = 1024;

    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final Map<String, Deque<Instant>> requestsByIp = new HashMap<>();
    private final Deque<Instant> globalRequests = new ArrayDeque<>();
    private final Clock clock;
    private final int maxTrackedAccounts;
    private final int maxTrackedIps;
    private final int maxRequestsPerIp;
    private final int maxGlobalRequests;

    public LoginAttemptService() {
        this(Clock.systemUTC(), 10_000, 4_096, 120, 2_000);
    }

    LoginAttemptService(Clock clock, int maxTrackedAccounts, int maxTrackedIps,
                        int maxRequestsPerIp, int maxGlobalRequests) {
        this.clock = clock;
        this.maxTrackedAccounts = maxTrackedAccounts;
        this.maxTrackedIps = maxTrackedIps;
        this.maxRequestsPerIp = maxRequestsPerIp;
        this.maxGlobalRequests = maxGlobalRequests;
    }

    /** Applied to every request before allocating username state or querying the database. */
    public synchronized boolean isRequestRateLimited(String clientIp) {
        Instant now = clock.instant();
        prune(globalRequests, now.minus(REQUEST_WINDOW));
        if (globalRequests.size() >= maxGlobalRequests) {
            return true;
        }
        String ip = clientIp == null || clientIp.isBlank() || clientIp.length() > 64 ? "unknown" : clientIp;
        Deque<Instant> ipRequests = requestsByIp.get(ip);
        if (ipRequests == null) {
            if (requestsByIp.size() >= maxTrackedIps) {
                removeExpiredAttempts();
                if (requestsByIp.size() >= maxTrackedIps) {
                    return true;
                }
            }
            ipRequests = new ArrayDeque<>();
            requestsByIp.put(ip, ipRequests);
        }
        prune(ipRequests, now.minus(REQUEST_WINDOW));
        if (ipRequests.size() >= maxRequestsPerIp) {
            return true;
        }
        ipRequests.addLast(now);
        globalRequests.addLast(now);
        return false;
    }

    public synchronized boolean isBlocked(String key) {
        if (!validKey(key)) {
            return true;
        }
        AttemptWindow attemptWindow = attempts.get(key);
        if (attemptWindow == null) {
            if (attempts.size() >= maxTrackedAccounts) {
                removeExpiredAttempts();
            }
            // Fail closed at capacity rather than evicting active account blocks.
            return attempts.size() >= maxTrackedAccounts;
        }

        Instant now = clock.instant();
        attemptWindow.pruneExpiredFailures(now);
        if (attemptWindow.blockedUntil != null) {
            return true;
        }

        if (attemptWindow.failures.isEmpty()) {
            attempts.remove(key);
        }
        return false;
    }

    public synchronized void recordFailure(String key) {
        if (!validKey(key)) {
            return;
        }
        if (!attempts.containsKey(key) && attempts.size() >= maxTrackedAccounts) {
            removeExpiredAttempts();
            if (attempts.size() >= maxTrackedAccounts) {
                return;
            }
        }
        AttemptWindow attemptWindow = attempts.computeIfAbsent(key, ignored -> new AttemptWindow());
        Instant now = clock.instant();
        attemptWindow.pruneExpiredFailures(now);
        if (attemptWindow.failures.size() < MAX_FAILURES) {
            attemptWindow.failures.addLast(now);
        }
        if (attemptWindow.failures.size() >= MAX_FAILURES) {
            attemptWindow.blockedUntil = now.plus(BLOCK_DURATION);
        }
    }

    public synchronized void recordSuccess(String key) {
        attempts.remove(key);
    }

    /** Reclaims idle one-off keys even if their usernames/IPs never appear again. */
    @Scheduled(fixedDelay = 60_000)
    public synchronized void removeExpiredAttempts() {
        Instant now = clock.instant();
        attempts.values().removeIf(window -> {
            window.pruneExpiredFailures(now);
            return window.failures.isEmpty() && window.blockedUntil == null;
        });
        requestsByIp.values().removeIf(requests -> {
            prune(requests, now.minus(REQUEST_WINDOW));
            return requests.isEmpty();
        });
        prune(globalRequests, now.minus(REQUEST_WINDOW));
    }

    private boolean validKey(String key) {
        return key != null && key.length() <= MAX_KEY_LENGTH;
    }

    private static void prune(Deque<Instant> timestamps, Instant cutoff) {
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
            timestamps.removeFirst();
        }
    }

    private static final class AttemptWindow {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;

        private void pruneExpiredFailures(Instant now) {
            prune(failures, now.minus(FAILURE_WINDOW));
            if (blockedUntil != null && !blockedUntil.isAfter(now)) {
                blockedUntil = null;
            }
        }
    }
}
