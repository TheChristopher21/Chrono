package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class PmsBackupVerifier {
    private final boolean enabled;
    private final Path directory;
    private final Duration maxAge;
    private final long minimumBytes;

    public PmsBackupVerifier(
            @Value("${app.backup.monitoring.enabled:${app.backup.enabled:false}}") boolean enabled,
            @Value("${app.backup.directory:${BACKUP_DIR:/var/backup}}") String directory,
            @Value("${app.backup.max-age:PT26H}") Duration maxAge,
            @Value("${app.backup.minimum-bytes:1024}") long minimumBytes) {
        this.enabled = enabled;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.maxAge = maxAge;
        this.minimumBytes = Math.max(1, minimumBytes);
    }

    public BackupCheck inspect() {
        if (!enabled) {
            return new BackupCheck(
                    HealthStatus.NOT_CONFIGURED,
                    "Automatische Datenbanksicherung ist nicht aktiviert.",
                    null,
                    false);
        }
        if (!Files.isDirectory(directory)) {
            return new BackupCheck(
                    HealthStatus.CRITICAL,
                    "Das konfigurierte Backup-Verzeichnis ist nicht erreichbar.",
                    null,
                    false);
        }
        try {
            Optional<Path> latest = latestBackup();
            if (latest.isEmpty()) {
                return new BackupCheck(
                        HealthStatus.CRITICAL,
                        "Im Backup-Verzeichnis wurde keine SQL-Sicherung gefunden.",
                        null,
                        false);
            }
            return verify(latest.get(), Instant.now());
        } catch (IOException exception) {
            return new BackupCheck(
                    HealthStatus.CRITICAL,
                    "Der Backup-Status konnte nicht gelesen werden.",
                    null,
                    false);
        }
    }

    public Optional<Path> latestVerifiedBackup() {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Optional<Path> latest = latestBackup();
            if (latest.isEmpty()) {
                return Optional.empty();
            }
            BackupCheck check = verify(latest.get(), Instant.now());
            return check.status() == HealthStatus.CRITICAL || !check.checksumValid()
                    ? Optional.empty()
                    : latest;
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    BackupCheck verify(Path backup, Instant now) throws IOException {
        Path normalized = backup.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory) || !Files.isRegularFile(normalized)) {
            return new BackupCheck(HealthStatus.CRITICAL, "Ungültiges Backup-Artefakt.", null, false);
        }
        long size = Files.size(normalized);
        LocalDateTime modifiedAt = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(normalized).toInstant(), ZoneId.systemDefault());
        if (size < minimumBytes) {
            return new BackupCheck(
                    HealthStatus.CRITICAL,
                    "Die neueste SQL-Sicherung ist unvollständig oder leer.",
                    modifiedAt,
                    false);
        }
        if (!containsRestoreFoundation(normalized)) {
            return new BackupCheck(
                    HealthStatus.CRITICAL,
                    "Die Sicherung enthält nicht alle für einen PMS-Restore erforderlichen Kerntabellen.",
                    modifiedAt,
                    false);
        }
        boolean checksumValid = hasValidChecksum(normalized);
        Duration age = Duration.between(
                Files.getLastModifiedTime(normalized).toInstant(), now);
        if (age.compareTo(maxAge) > 0) {
            return new BackupCheck(
                    HealthStatus.WARNING,
                    "Die neueste SQL-Sicherung ist älter als das erlaubte Sicherungsfenster.",
                    modifiedAt,
                    checksumValid);
        }
        if (!checksumValid) {
            return new BackupCheck(
                    HealthStatus.WARNING,
                    "Die SQL-Sicherung hat noch keine gültige SHA-256-Prüfsumme.",
                    modifiedAt,
                    false);
        }
        return new BackupCheck(
                HealthStatus.OK,
                "Aktuelle SQL-Sicherung mit gültiger SHA-256-Prüfsumme vorhanden.",
                modifiedAt,
                true);
    }

    private Optional<Path> latestBackup() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .max(Comparator.comparingLong(this::lastModified));
        }
    }

    private boolean hasValidChecksum(Path backup) throws IOException {
        Path checksumFile = backup.resolveSibling(backup.getFileName() + ".sha256");
        if (!Files.isRegularFile(checksumFile)) {
            return false;
        }
        String expected = Files.readString(checksumFile).trim().split("\\s+")[0];
        return expected.equalsIgnoreCase(sha256(backup));
    }

    private boolean containsRestoreFoundation(Path backup) throws IOException {
        Set<String> missing = new HashSet<>(Set.of(
                "pms_properties",
                "pms_reservations",
                "pms_audit_events",
                "pms_integration_outbox"));
        try (BufferedReader reader = Files.newBufferedReader(backup)) {
            String line;
            while ((line = reader.readLine()) != null && !missing.isEmpty()) {
                missing.removeIf(line::contains);
            }
        }
        return missing.isEmpty();
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    public record BackupCheck(
            HealthStatus status,
            String summary,
            LocalDateTime latestBackupAt,
            boolean checksumValid
    ) {
    }
}
