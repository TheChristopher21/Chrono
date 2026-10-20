package com.chrono.chrono.services.pms;

import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsLiveUpdateServiceTest {
    final PmsPropertyAccessService access = mock(PmsPropertyAccessService.class);
    final PmsAuditEventRepository audit = mock(PmsAuditEventRepository.class);
    final PmsLiveUpdateService live = new PmsLiveUpdateService(access, audit);
    final PmsPropertyAccessService.Access actor = new PmsPropertyAccessService.Access(1L, 4L, false, Map.of(9L, Map.of("HOUSEKEEPING", "VIEW")));
    PmsLiveUpdateServiceTest() { when(access.access("staff")).thenReturn(actor); when(audit.liveSequences(anyLong(), anyCollection())).thenReturn(List.of()); }
    @AfterEach void close() { live.stop(); }
    @Test void requiresCurrentHotelAccessAndNeverExtendsJwtLifetime() {
        var emitter = live.subscribe("staff", 9L, Instant.now().plusSeconds(20));
        assertThat(emitter.getTimeout()).isBetween(1L, 20_000L);
        verify(access).require(actor, 9L, null, false);
        assertThatThrownBy(() -> live.subscribe("staff", 9L, Instant.now().minusSeconds(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).require(actor, 8L, null, false);
        assertThatThrownBy(() -> live.subscribe("staff", 8L, Instant.now().plusSeconds(20))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void limitsConnectionsAndReleasesExpiredSlots() {
        for (int index = 0; index < 12; index++) live.subscribe("staff", 9L, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        live.poll(Instant.now().plusSeconds(65));
        assertThatCode(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60))).doesNotThrowAnyException();
    }
    @Test void rechecksGrantsAtHeartbeatAndBatchesPropertySequencesPerTenant() {
        for (int index = 0; index < 12; index++) live.subscribe("staff", 9L, Instant.now().plusSeconds(120));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).require(actor, 9L, null, false);
        live.poll(Instant.now().plusSeconds(16));
        verify(audit, times(1)).liveSequences(4L, Set.of(9L));
        doNothing().when(access).require(actor, 9L, null, false);
        assertThatCode(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60))).doesNotThrowAnyException();
    }
    @Test void rapidReconnectsReplaceOnlyTheSameBrowsersHotelStreamWithoutRaisingTheQuota() {
        Instant expiry = Instant.now().plusSeconds(120);
        for (int browser = 0; browser < 12; browser++) live.subscribe("staff", 9L, expiry, "browser-" + browser);
        for (int reconnect = 0; reconnect < 30; reconnect++) {
            assertThatCode(() -> live.subscribe("staff", 9L, expiry, "browser-0")).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> live.subscribe("staff", 9L, expiry, "new-browser"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        // Reusing a client ID on a different property cannot evict another property's stream.
        assertThatThrownBy(() -> live.subscribe("staff", 8L, expiry, "browser-0"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
    @Test void anUnauthorizedReconnectCannotCloseTheExistingConnection() {
        Instant expiry = Instant.now().plusSeconds(120);
        for (int browser = 0; browser < 12; browser++) live.subscribe("staff", 9L, expiry, "browser-" + browser);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).require(actor, 9L, null, false);
        assertThatThrownBy(() -> live.subscribe("staff", 9L, expiry, "browser-0"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        doNothing().when(access).require(actor, 9L, null, false);
        assertThatThrownBy(() -> live.subscribe("staff", 9L, expiry, "new-browser"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
    @Test void anotherUserCannotReplaceTheOriginalUsersConnectionWithTheSameClientId() {
        Instant expiry = Instant.now().plusSeconds(120);
        for (int browser = 0; browser < 12; browser++) live.subscribe("staff", 9L, expiry, "browser-" + browser);
        when(access.access("other-staff")).thenReturn(actor);
        assertThatCode(() -> live.subscribe("other-staff", 9L, expiry, "browser-0")).doesNotThrowAnyException();
        assertThatThrownBy(() -> live.subscribe("staff", 9L, expiry, "new-browser"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
    @Test void releasesRotatedStreamsEvenIfTheServletHasNotReportedCompletion() {
        for (int browser = 0; browser < 12; browser++) live.subscribe("staff", 9L, Instant.now().plusSeconds(3600));
        live.poll(Instant.now().plusSeconds(301));
        assertThatCode(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60))).doesNotThrowAnyException();
    }
    @Test void rejectsMalformedClientIds() {
        assertThatThrownBy(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60), "x".repeat(81)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    @Test void aRecycledServletResponseCannotBlockReplacementOrShutdownOfOtherStreams() {
        try (var emitters = mockConstruction(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class,
                (emitter, context) -> doThrow(new IllegalStateException("Response already recycled")).when(emitter).complete())) {
            live.subscribe("staff", 9L, Instant.now().plusSeconds(60), "browser-0");
            assertThatCode(() -> live.subscribe("staff", 9L, Instant.now().plusSeconds(60), "browser-0")).doesNotThrowAnyException();
            live.subscribe("staff", 9L, Instant.now().plusSeconds(60), "browser-1");
            assertThatCode(live::stop).doesNotThrowAnyException();
            emitters.constructed().forEach(emitter -> verify(emitter).complete());
        }
    }
}
