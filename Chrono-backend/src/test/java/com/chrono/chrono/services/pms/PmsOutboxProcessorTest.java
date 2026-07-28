package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.IntegrationOutboxEvent;
import com.chrono.chrono.entities.pms.OutboxStatus;
import com.chrono.chrono.repositories.pms.IntegrationOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmsOutboxProcessorTest {

    @Test
    void deliversSupportedEventsAndUsesStableIdempotencyKey() {
        IntegrationOutboxRepository repository = mock(IntegrationOutboxRepository.class);
        IntegrationOutboxEvent event = event(41L, 0);
        AtomicInteger deliveredId = new AtomicInteger();
        PmsOutboxTransport transport = new PmsOutboxTransport() {
            @Override
            public boolean supports(PmsOutboxMessage message) {
                return message.eventType().startsWith("reservation.");
            }

            @Override
            public void deliver(PmsOutboxMessage message) {
                deliveredId.set(message.id().intValue());
            }
        };
        when(repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        when(repository.findTop50ByStatusAndLockedAtBeforeOrderByLockedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(repository.findLockedById(41L)).thenReturn(Optional.of(event));

        int delivered = new PmsOutboxProcessor(
                repository, List.of(transport), 3, Duration.ofSeconds(1), Duration.ofMinutes(10))
                .processDueEvents("worker-1");

        assertThat(delivered).isOne();
        assertThat(deliveredId).hasValue(41);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        assertThat(event.getAttemptCount()).isOne();
        assertThat(event.getDeliveredAt()).isNotNull();
        assertThat(event.getLockOwner()).isNull();
    }

    @Test
    void movesRepeatedFailuresToDeadLetter() {
        IntegrationOutboxRepository repository = mock(IntegrationOutboxRepository.class);
        IntegrationOutboxEvent event = event(42L, 1);
        PmsOutboxTransport transport = new PmsOutboxTransport() {
            @Override
            public boolean supports(PmsOutboxMessage message) {
                return true;
            }

            @Override
            public void deliver(PmsOutboxMessage message) {
                throw new IllegalStateException("Provider nicht erreichbar");
            }
        };
        when(repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        when(repository.findTop50ByStatusAndLockedAtBeforeOrderByLockedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(repository.findLockedById(42L)).thenReturn(Optional.of(event));

        new PmsOutboxProcessor(
                repository, List.of(transport), 2, Duration.ofSeconds(1), Duration.ofMinutes(10))
                .processDueEvents("worker-2");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getLastError()).contains("Provider nicht erreichbar");
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void leavesEventsPendingUntilAProviderAdapterIsConfigured() {
        IntegrationOutboxRepository repository = mock(IntegrationOutboxRepository.class);
        IntegrationOutboxEvent event = event(43L, 0);
        when(repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        when(repository.findTop50ByStatusAndLockedAtBeforeOrderByLockedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(repository.findLockedById(43L)).thenReturn(Optional.of(event));

        int delivered = new PmsOutboxProcessor(
                repository, List.of(), 3, Duration.ofSeconds(1), Duration.ofMinutes(10))
                .processDueEvents("worker-3");

        assertThat(delivered).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
    }

    private IntegrationOutboxEvent event(Long id, int attempts) {
        HotelProperty property = mock(HotelProperty.class);
        when(property.getId()).thenReturn(7L);
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setId(id);
        event.setProperty(property);
        event.setEventType("reservation.created");
        event.setAggregateType("reservation");
        event.setAggregateId("99");
        event.setPayload("{\"reservationId\":99}");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(attempts);
        event.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        return event;
    }
}
