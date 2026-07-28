package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.IntegrationOutboxEvent;
import com.chrono.chrono.entities.pms.OutboxStatus;
import com.chrono.chrono.repositories.pms.IntegrationOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PmsOutboxProcessor {
    private final IntegrationOutboxRepository repository;
    private final List<PmsOutboxTransport> transports;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration lockTimeout;

    public PmsOutboxProcessor(
            IntegrationOutboxRepository repository,
            List<PmsOutboxTransport> transports,
            @Value("${app.pms.outbox.max-attempts:8}") int maxAttempts,
            @Value("${app.pms.outbox.initial-backoff:PT30S}") Duration initialBackoff,
            @Value("${app.pms.outbox.lock-timeout:PT10M}") Duration lockTimeout) {
        this.repository = repository;
        this.transports = transports;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff.isNegative() || initialBackoff.isZero()
                ? Duration.ofSeconds(1) : initialBackoff;
        this.lockTimeout = lockTimeout.isNegative() || lockTimeout.isZero()
                ? Duration.ofMinutes(10) : lockTimeout;
    }

    @Transactional
    public int processDueEvents(String workerId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        recoverStaleClaims(now);
        List<Long> candidates = repository
                .findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), now)
                .stream().map(IntegrationOutboxEvent::getId).toList();
        int delivered = 0;
        for (Long eventId : candidates) {
            IntegrationOutboxEvent event = repository.findLockedById(eventId).orElse(null);
            if (event == null || !isDue(event, now)) {
                continue;
            }
            int attemptNumber = event.getAttemptCount() + 1;
            PmsOutboxMessage message = new PmsOutboxMessage(
                    event.getId(), event.getProperty().getId(), event.getEventType(),
                    event.getAggregateType(), event.getAggregateId(), event.getPayload(), attemptNumber);
            PmsOutboxTransport transport = transports.stream()
                    .filter(candidate -> candidate.supports(message)).findFirst().orElse(null);
            // An event without a configured provider remains pending and visible.
            if (transport == null) {
                continue;
            }
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedAt(now);
            event.setLockOwner(clean(workerId));
            event.setLastAttemptAt(now);
            event.setAttemptCount(attemptNumber);
            repository.flush();
            try {
                transport.deliver(message);
                event.setStatus(OutboxStatus.DELIVERED);
                event.setDeliveredAt(now);
                event.setNextAttemptAt(null);
                event.setLastError(null);
                delivered++;
            } catch (Exception exception) {
                event.setLastError(errorMessage(exception));
                if (attemptNumber >= maxAttempts) {
                    event.setStatus(OutboxStatus.DEAD_LETTER);
                    event.setNextAttemptAt(null);
                } else {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setNextAttemptAt(now.plus(backoffFor(attemptNumber)));
                }
            } finally {
                event.setLockedAt(null);
                event.setLockOwner(null);
            }
        }
        return delivered;
    }

    private void recoverStaleClaims(LocalDateTime now) {
        repository.findTop50ByStatusAndLockedAtBeforeOrderByLockedAtAsc(
                        OutboxStatus.PROCESSING, now.minus(lockTimeout))
                .forEach(event -> {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setLastError("Veraltete Verarbeitungssperre wurde automatisch freigegeben.");
                    event.setNextAttemptAt(now);
                    event.setLockedAt(null);
                    event.setLockOwner(null);
                });
    }

    private boolean isDue(IntegrationOutboxEvent event, LocalDateTime now) {
        return (event.getStatus() == OutboxStatus.PENDING || event.getStatus() == OutboxStatus.FAILED)
                && (event.getNextAttemptAt() == null || !event.getNextAttemptAt().isAfter(now));
    }

    private Duration backoffFor(int attemptNumber) {
        long multiplier = 1L << Math.min(10, Math.max(0, attemptNumber - 1));
        Duration calculated = initialBackoff.multipliedBy(multiplier);
        return calculated.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : calculated;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? "pms-outbox-worker" : value.trim();
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
