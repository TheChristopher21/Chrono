package com.chrono.chrono.services.pms;

import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** DB-backed invalidations work across application instances. No guest or financial data is broadcast. */
@Service
public class PmsLiveUpdateService {
    private final PmsPropertyAccessService access;
    private final PmsAuditEventRepository audit;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "pms-live-poll"));
    private final ExecutorService senders = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000), r -> daemon(r, "pms-live-send"), new ThreadPoolExecutor.AbortPolicy());
    public PmsLiveUpdateService(PmsPropertyAccessService access, PmsAuditEventRepository audit) { this.access = access; this.audit = audit; }
    private static Thread daemon(Runnable work, String name) { Thread thread = new Thread(work, name); thread.setDaemon(true); return thread; }
    @PostConstruct void start() { scheduler.scheduleWithFixedDelay(() -> { try { poll(Instant.now()); } catch (RuntimeException ignored) { /* Clients keep the bounded HTTP refresh fallback. */ } }, 1, 1, TimeUnit.SECONDS); }
    @PreDestroy void stop() { scheduler.shutdownNow(); connections.forEach(this::close); senders.shutdownNow(); }

    public synchronized SseEmitter subscribe(String username, Long propertyId, Instant tokenExpiresAt) {
        var actor = access.access(username); access.require(actor, propertyId, null, false);
        if (!tokenExpiresAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (connections.size() >= 1000 || connections.stream().filter(value -> value.username.equals(username)).count() >= 12)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Zu viele Live-Verbindungen.");
        // Rotate every five minutes, and never retain a connection beyond its JWT lifetime.
        long timeout = Math.max(1, Math.min(300_000, tokenExpiresAt.toEpochMilli() - System.currentTimeMillis()));
        SseEmitter emitter = new SseEmitter(timeout);
        Long sequence = audit.findMaximumSequence(propertyId);
        Connection connection = new Connection(username, actor.companyId(), propertyId, tokenExpiresAt, emitter, sequence == null ? 0 : sequence);
        connections.add(connection);
        emitter.onCompletion(() -> connections.remove(connection)); emitter.onTimeout(() -> close(connection)); emitter.onError(error -> connections.remove(connection));
        try { emitter.send(SseEmitter.event().name("ready").data("{}").reconnectTime(3000)); }
        catch (IOException failure) { close(connection); }
        return emitter;
    }

    void poll(Instant now) {
        for (var group : connections.stream().collect(Collectors.groupingBy(value -> value.companyId)).entrySet()) {
            var propertyIds = group.getValue().stream().map(value -> value.propertyId).collect(Collectors.toSet());
            Map<Long, Long> latest = new HashMap<>();
            for (Object[] row : audit.liveSequences(group.getKey(), propertyIds)) latest.put((Long) row[0], row[1] == null ? 0 : ((Number) row[1]).longValue());
            Map<String, PmsPropertyAccessService.Access> actors = new HashMap<>();
            for (Connection connection : group.getValue()) {
                if (!connection.expiresAt.isAfter(now)) { close(connection); continue; }
                long sequence = latest.getOrDefault(connection.propertyId, 0L);
                boolean changed = sequence != connection.sequence;
                if (!changed && now.toEpochMilli() - connection.lastSent < 15_000) continue;
                try {
                    var actor = actors.computeIfAbsent(connection.username, access::access);
                    if (!actor.companyId().equals(connection.companyId)) { close(connection); continue; }
                    access.require(actor, connection.propertyId, null, false);
                    if (!connection.sending.compareAndSet(false, true)) continue;
                    senders.execute(() -> {
                        try {
                            if (!connections.contains(connection) || !connection.expiresAt.isAfter(Instant.now())) { close(connection); return; }
                            connection.emitter.send(changed ? SseEmitter.event().name("changed").id(Long.toString(sequence)).data("{}") : SseEmitter.event().comment("heartbeat"));
                            connection.sequence = sequence; connection.lastSent = now.toEpochMilli();
                        } catch (IOException | RuntimeException failure) { close(connection); }
                        finally { connection.sending.set(false); }
                    });
                } catch (RuntimeException deniedOrFull) { close(connection); }
            }
        }
    }
    private void close(Connection connection) { connections.remove(connection); connection.emitter.complete(); }
    private static final class Connection {
        final String username; final Long companyId; final Long propertyId; final Instant expiresAt; final SseEmitter emitter;
        final AtomicBoolean sending = new AtomicBoolean(); volatile long sequence; volatile long lastSent = System.currentTimeMillis();
        Connection(String username, Long companyId, Long propertyId, Instant expiresAt, SseEmitter emitter, long sequence) {
            this.username = username; this.companyId = companyId; this.propertyId = propertyId; this.expiresAt = expiresAt; this.emitter = emitter; this.sequence = sequence;
        }
    }
}
