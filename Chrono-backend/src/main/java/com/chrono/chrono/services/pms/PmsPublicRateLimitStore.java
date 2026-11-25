package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.PublicRateLimit;
import com.chrono.chrono.repositories.pms.PublicRateLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PmsPublicRateLimitStore {
    private final PublicRateLimitRepository repository;

    public PmsPublicRateLimitStore(PublicRateLimitRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Consumption consume(String rateKey, LocalDateTime windowStartedAt, LocalDateTime now) {
        PublicRateLimit quota = repository.findByRateKeyForUpdate(rateKey).orElse(null);
        if (quota == null) {
            quota = new PublicRateLimit();
            quota.setRateKey(rateKey);
            quota.setWindowStartedAt(windowStartedAt);
            quota.setRequestCount(1);
        } else if (!quota.getWindowStartedAt().equals(windowStartedAt)) {
            quota.setWindowStartedAt(windowStartedAt);
            quota.setRequestCount(1);
        } else {
            quota.setRequestCount(quota.getRequestCount() + 1);
        }
        quota.setUpdatedAt(now);
        repository.saveAndFlush(quota);
        return new Consumption(quota.getRequestCount(), quota.getWindowStartedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteStale(LocalDateTime cutoff) {
        return repository.deleteStale(cutoff);
    }

    public record Consumption(int requestCount, LocalDateTime windowStartedAt) {}
}
