package com.chrono.chrono.services.pms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PmsPublicRateLimiter {

    private final int maxRequests;
    private final long windowSeconds;
    private final int maxLocalBuckets;
    private final PmsPublicRateLimitStore store;
    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    @Autowired
    public PmsPublicRateLimiter(
            PmsPublicRateLimitStore store,
            @Value("${app.pms.public-rate-limit.max-requests:30}") int maxRequests,
            @Value("${app.pms.public-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.pms.public-rate-limit.max-local-buckets:10000}") int maxLocalBuckets) {
        this.store = store;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxLocalBuckets = Math.max(100, maxLocalBuckets);
    }

    public PmsPublicRateLimiter(int maxRequests, long windowSeconds) {
        this.store = null;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxLocalBuckets = 10_000;
    }

    public Decision check(String remoteAddress, String operation) {
        String client = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
        String key = digest(client + ":" + operation);
        Instant now = Instant.now();
        if (store != null) {
            return checkDistributed(key, now);
        }
        return checkLocal(key, now);
    }

    private Decision checkDistributed(String key, Instant now) {
        long startEpoch = now.getEpochSecond() - Math.floorMod(now.getEpochSecond(), windowSeconds);
        LocalDateTime windowStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(startEpoch), ZoneOffset.UTC);
        LocalDateTime checkedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        PmsPublicRateLimitStore.Consumption consumption;
        try {
            consumption = store.consume(key, windowStart, checkedAt);
        } catch (DataIntegrityViolationException raceOnFirstRequest) {
            consumption = store.consume(key, windowStart, checkedAt);
        }
        if (consumption.requestCount() > maxRequests) {
            long retryAfter = startEpoch + windowSeconds - now.getEpochSecond();
            return new Decision(false, Math.max(1, retryAfter));
        }
        return new Decision(true, 0);
    }

    private Decision checkLocal(String key, Instant now) {
        pruneLocalBuckets(now);
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

    private void pruneLocalBuckets(Instant now) {
        if (requests.size() < maxLocalBuckets) return;
        Instant cutoff = now.minusSeconds(windowSeconds);
        requests.entrySet().removeIf(entry -> {
            Deque<Instant> bucket = entry.getValue();
            synchronized (bucket) {
                return bucket.isEmpty() || bucket.peekLast().isBefore(cutoff);
            }
        });
        if (requests.size() >= maxLocalBuckets) {
            requests.keySet().stream().limit(Math.max(1, maxLocalBuckets / 20L)).toList()
                    .forEach(requests::remove);
        }
    }

    @Scheduled(fixedDelayString = "${app.pms.public-rate-limit.cleanup-interval-ms:3600000}")
    public void deleteStaleDistributedBuckets() {
        if (store != null) {
            store.deleteStale(LocalDateTime.now(ZoneOffset.UTC).minus(
                    Duration.ofSeconds(Math.max(windowSeconds * 2, 3600))));
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
