package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatRateLimiter {

    @Value("${chat.rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${chat.rate-limit.window-seconds:300}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitDecision check(User user) {
        String key = keyFor(user);
        Instant now = Instant.now();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        synchronized (bucket) {
            long elapsedSeconds = Math.max(0, Duration.between(bucket.windowStart, now).getSeconds());
            if (elapsedSeconds >= getWindowSeconds()) {
                bucket.windowStart = now;
                bucket.count = 0;
                elapsedSeconds = 0;
            }
            if (bucket.count >= getMaxRequests()) {
                long retryAfterSeconds = Math.max(1, getWindowSeconds() - elapsedSeconds);
                return new RateLimitDecision(false, 0, retryAfterSeconds, getMaxRequests(), getWindowSeconds());
            }
            bucket.count++;
            int remaining = Math.max(0, getMaxRequests() - bucket.count);
            return new RateLimitDecision(true, remaining, 0, getMaxRequests(), getWindowSeconds());
        }
    }

    public int getMaxRequests() {
        return Math.max(1, maxRequests);
    }

    public long getWindowSeconds() {
        return Math.max(30, windowSeconds);
    }

    private String keyFor(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "anonymous";
        }
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        return (companyId != null ? companyId : "no-company") + ":" + user.getUsername().toLowerCase(Locale.ROOT);
    }

    public record RateLimitDecision(boolean allowed, int remainingRequests, long retryAfterSeconds, int maxRequests, long windowSeconds) {
    }

    private static final class Bucket {
        private Instant windowStart;
        private int count;

        private Bucket(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
