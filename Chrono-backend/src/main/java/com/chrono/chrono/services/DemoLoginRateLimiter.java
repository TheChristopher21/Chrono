package com.chrono.chrono.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoLoginRateLimiter {

    @Value("${app.demo-login.rate-limit.max-requests-per-ip:5}")
    private int maxRequestsPerIp;

    @Value("${app.demo-login.rate-limit.global-max-requests:100}")
    private int globalMaxRequests;

    @Value("${app.demo-login.rate-limit.window-seconds:3600}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();
    private final Deque<Instant> globalAttempts = new ArrayDeque<>();

    public RateLimitDecision check(String clientIp) {
        Instant now = Instant.now();
        long effectiveWindowSeconds = Math.max(1, windowSeconds);

        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        Deque<Instant> ipAttempts = attemptsByIp.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (ipAttempts) {
            prune(ipAttempts, now, effectiveWindowSeconds);
            if (maxRequestsPerIp > 0 && ipAttempts.size() >= maxRequestsPerIp) {
                return denied(ipAttempts, now, effectiveWindowSeconds);
            }

            synchronized (globalAttempts) {
                prune(globalAttempts, now, effectiveWindowSeconds);
                if (globalMaxRequests > 0 && globalAttempts.size() >= globalMaxRequests) {
                    return denied(globalAttempts, now, effectiveWindowSeconds);
                }
                ipAttempts.addLast(now);
                globalAttempts.addLast(now);
            }
        }

        return new RateLimitDecision(true, 0);
    }

    private RateLimitDecision denied(Deque<Instant> attempts, Instant now, long effectiveWindowSeconds) {
        Instant oldest = attempts.peekFirst();
        long retryAfter = oldest == null ? effectiveWindowSeconds : effectiveWindowSeconds - (now.getEpochSecond() - oldest.getEpochSecond());
        return new RateLimitDecision(false, Math.max(1, retryAfter));
    }

    private void prune(Deque<Instant> attempts, Instant now, long effectiveWindowSeconds) {
        Instant cutoff = now.minusSeconds(effectiveWindowSeconds);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}
