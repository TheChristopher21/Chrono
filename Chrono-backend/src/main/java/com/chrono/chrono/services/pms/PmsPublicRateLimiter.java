package com.chrono.chrono.services.pms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PmsPublicRateLimiter {

    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public PmsPublicRateLimiter(
            @Value("${app.pms.public-rate-limit.max-requests:30}") int maxRequests,
            @Value("${app.pms.public-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public Decision check(String remoteAddress, String operation) {
        String ip = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
        String key = ip + ":" + operation;
        Instant now = Instant.now();
        Deque<Instant> bucket = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant cutoff = now.minusSeconds(windowSeconds);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.removeFirst();
            }
            if (bucket.size() >= maxRequests) {
                long retryAfter = windowSeconds - (now.getEpochSecond() - bucket.peekFirst().getEpochSecond());
                return new Decision(false, Math.max(1, retryAfter));
            }
            bucket.addLast(now);
            return new Decision(true, 0);
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
