package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.Set;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@Service
public class PmsBackupVerifier {
    private static final String LATEST_BACKUP_MARKER = "latest.ok";
    private static final Duration VERIFICATION_CACHE_TTL = Duration.ofSeconds(60);
    static final Set<String> RESTORE_FOUNDATION = Set.of(
            "pms_properties", "pms_reservations", "pms_audit_events", "pms_integration_outbox");

    private final boolean enabled;
    private final Path directory;
    private final Duration maxAge;
    private final long minimumBytes;
    private CachedVerification cachedVerification;

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
                        "Keine abgeschlossene reguläre SQL-Sicherung (.sql oder .sql.gz) gefunden; latest.ok und Backup-Dienst prüfen.",
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

    synchronized BackupCheck verify(Path backup, Instant now) throws IOException {
        Path normalized = backup.toAbsolutePath().normalize();
        if (!isBackupArtifact(normalized)) {
            return new BackupCheck(HealthStatus.CRITICAL, "Ungültiges Backup-Artefakt.", null, false);
        }
        VerificationKey key = verificationKey(normalized);
        LocalDateTime modifiedAt = LocalDateTime.ofInstant(
                key.backup().modifiedAt().toInstant(), ZoneId.systemDefault());
        ContentVerification content;
        if (cachedVerification != null && cachedVerification.key().equals(key)
                && !now.isBefore(cachedVerification.checkedAt())
                && now.isBefore(cachedVerification.checkedAt().plus(VERIFICATION_CACHE_TTL))) {
            content = cachedVerification.content();
        } else {
            content = verifyContents(normalized, key);
            // A dump or sidecar being replaced during a scan cannot produce a trusted cached result.
            if (!isBackupArtifact(normalized) || !key.equals(verificationKey(normalized))) {
                cachedVerification = null;
                return new BackupCheck(HealthStatus.CRITICAL,
                        "Backup-Artefakt oder Bestätigung wurde während der Prüfung geändert; erneute Prüfung erforderlich.",
                        modifiedAt, false);
            }
            cachedVerification = new CachedVerification(key, now, content);
        }
        if (content.failure() != null) {
            return new BackupCheck(HealthStatus.CRITICAL, content.failure(), modifiedAt, false);
        }
        // Age is deliberately not cached: a previously healthy artifact can cross the backup window.
        Duration age = Duration.between(key.backup().modifiedAt().toInstant(), now);
        if (age.compareTo(maxAge) > 0) {
            return new BackupCheck(
                    HealthStatus.WARNING,
                    "Die neueste SQL-Sicherung ist älter als das erlaubte Sicherungsfenster.",
                    modifiedAt,
                    content.checksumValid());
        }
        if (!content.checksumValid()) {
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

    private ContentVerification verifyContents(Path normalized, VerificationKey key) throws IOException {
        if (key.backup().size() < minimumBytes) {
            return new ContentVerification("Die neueste SQL-Sicherung ist unvollständig oder leer.", false);
        }
        Set<String> missing;
        try {
            missing = missingRestoreFoundation(normalized);
        } catch (IOException exception) {
            return new ContentVerification(
                    "Die SQL-Sicherung ist nicht vollständig lesbar oder das komprimierte Archiv ist beschädigt.",
                    false);
        }
        if (!missing.isEmpty()) {
            return new ContentVerification(
                    "Der Sicherung " + normalized.getFileName() + " fehlen CREATE-TABLE-Definitionen für PMS-Kerntabellen: "
                            + String.join(", ", missing) + ".",
                    false);
        }
        String expected = key.checksum().content() == null ? "" : key.checksum().content().trim().split("\\s+", 2)[0];
        return new ContentVerification(null,
                expected.matches("[a-fA-F0-9]{64}") && expected.equalsIgnoreCase(sha256(normalized)));
    }

    private Optional<Path> latestBackup() throws IOException {
        Path marker = directory.resolve(LATEST_BACKUP_MARKER).normalize();
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            String markerContent = smallFileSnapshot(marker).content();
            String markerValue = markerContent == null ? null : markerContent.lines().findFirst().orElse(null);
            if (markerValue == null || markerValue.isBlank()) {
                return Optional.empty();
            }

            Path markedBackup;
            try {
                markedBackup = Path.of(markerValue.trim());
            } catch (java.nio.file.InvalidPathException exception) {
                return Optional.empty();
            }
            if (!markedBackup.isAbsolute()) {
                markedBackup = directory.resolve(markedBackup);
            }
            markedBackup = markedBackup.toAbsolutePath().normalize();
            if (!isBackupArtifact(markedBackup)) {
                return Optional.empty();
            }
            return Optional.of(markedBackup);
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(this::isBackupArtifact)
                    .max(Comparator.comparingLong(this::lastModified));
        }
    }

    private VerificationKey verificationKey(Path backup) throws IOException {
        FileIdentity identity = fileIdentity(backup);
        if (identity == null || !identity.regular()) throw new IOException("Backup no longer exists");
        return new VerificationKey(backup, identity,
                smallFileSnapshot(backup.resolveSibling(backup.getFileName() + ".sha256")),
                smallFileSnapshot(directory.resolve(LATEST_BACKUP_MARKER)));
    }

    private SmallFileSnapshot smallFileSnapshot(Path file) throws IOException {
        FileIdentity identity = fileIdentity(file);
        if (identity == null || !identity.regular() || identity.size() > 4096 || !isSafeRegularFile(file)) {
            return new SmallFileSnapshot(identity, null);
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(4097);
            return new SmallFileSnapshot(identity, bytes.length > 4096 ? null : new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private FileIdentity fileIdentity(Path file) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new FileIdentity(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime(), attributes.isRegularFile());
        } catch (NoSuchFileException exception) {
            return null;
        }
    }

    private record FileIdentity(Object fileKey, long size, FileTime modifiedAt, boolean regular) { }
    private record SmallFileSnapshot(FileIdentity identity, String content) { }
    private record VerificationKey(Path path, FileIdentity backup, SmallFileSnapshot checksum, SmallFileSnapshot marker) { }
    private record ContentVerification(String failure, boolean checksumValid) { }
    private record CachedVerification(VerificationKey key, Instant checkedAt, ContentVerification content) { }

    private boolean isBackupArtifact(Path path) {
        String name = path.getFileName().toString();
        // Pre-deploy dumps protect rollback of the old release; they are not routine backups.
        return !name.startsWith(".") && !name.startsWith("predeploy-")
                && (name.endsWith(".sql") || name.endsWith(".sql.gz")) && isSafeRegularFile(path);
    }

    private boolean isSafeRegularFile(Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            return normalized.startsWith(directory) && Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    && normalized.toRealPath().startsWith(directory.toRealPath());
        } catch (IOException exception) {
            return false;
        }
    }

    static InputStream openSqlInput(Path backup) throws IOException {
        InputStream input = new BufferedInputStream(Files.newInputStream(backup));
        try {
            return backup.getFileName().toString().endsWith(".sql.gz") ? new GZIPInputStream(input) : input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    Set<String> missingRestoreFoundation(Path backup) throws IOException {
        Set<String> missing = new TreeSet<>(RESTORE_FOUNDATION);
        try (InputStream sql = openSqlInput(backup);
                Reader reader = new InputStreamReader(sql, StandardCharsets.UTF_8)) {
            SqlTokens tokens = new SqlTokens(reader);
            String token;
            while (!missing.isEmpty() && (token = tokens.next()) != null) {
                if (!"create".equals(token)) continue;
                token = tokens.next();
                // Temporary tables disappear with the restore connection and prove no persisted data.
                if ("temporary".equals(token)) continue;
                if (!"table".equals(token)) continue;
                token = tokens.next();
                if ("if".equals(token)) {
                    if (!"not".equals(tokens.next()) || !"exists".equals(tokens.next())) continue;
                    token = tokens.next();
                }
                String table = token;
                token = tokens.next();
                if (".".equals(token)) {
                    table = tokens.next();
                    token = tokens.next();
                }
                if ("(".equals(token) && table != null) missing.remove(table);
            }
            // SQL parsing can stop, but gzip must reach its trailer to detect CRC/truncation errors.
            if (backup.getFileName().toString().endsWith(".sql.gz")) sql.transferTo(OutputStream.nullOutputStream());
        }
        return missing;
    }

    /** Bounded tokens keep multi-gigabyte mysqldump INSERT lines out of memory. */
    private static final class SqlTokens {
        private final Reader reader;
        private final char[] buffer = new char[32_768];
        private int position;
        private int count;
        private int pushedBack = -1;

        private SqlTokens(Reader reader) {
            this.reader = reader;
        }

        private int read() throws IOException {
            if (pushedBack != -1) {
                int value = pushedBack;
                pushedBack = -1;
                return value;
            }
            if (count == -1) return -1;
            if (position == count) {
                count = reader.read(buffer);
                position = 0;
                if (count == -1) return -1;
            }
            return buffer[position++];
        }

        private void unread(int value) {
            pushedBack = value;
        }

        private String next() throws IOException {
            int ch;
            while ((ch = read()) != -1) {
                if (Character.isWhitespace(ch)) continue;
                if (ch == '#') { skipLine(); continue; }
                if (ch == '-' || ch == '/') {
                    int following = read();
                    if (ch == '-' && following == '-') { skipLine(); continue; }
                    if (ch == '/' && following == '*') { skipComment(); continue; }
                    if (following != -1) unread(following);
                }
                if (ch == '\'' || ch == '"') { quoted(ch, false); return "'"; }
                if (ch == '`') return quoted(ch, true);
                if (Character.isLetterOrDigit(ch) || ch == '_') {
                    StringBuilder token = new StringBuilder();
                    do {
                        if (token.length() < 256) token.append((char) ch);
                        ch = read();
                    } while (ch != -1 && (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$'));
                    if (ch != -1) unread(ch);
                    return token.toString().toLowerCase(Locale.ROOT);
                }
                return Character.toString((char) ch);
            }
            return null;
        }

        private String quoted(int quote, boolean identifier) throws IOException {
            StringBuilder token = new StringBuilder();
            int ch;
            while ((ch = read()) != -1) {
                if (ch == '\\' && !identifier) { read(); continue; }
                if (ch == quote) {
                    int following = read();
                    if (following != quote) {
                        if (following != -1) unread(following);
                        return token.toString().toLowerCase(Locale.ROOT);
                    }
                }
                if (identifier && token.length() < 256) token.append((char) ch);
            }
            throw new IOException("Unterminated SQL quote");
        }

        private void skipLine() throws IOException {
            int ch;
            while ((ch = read()) != -1 && ch != '\n' && ch != '\r') { }
        }

        private void skipComment() throws IOException {
            int previous = 0;
            int ch;
            while ((ch = read()) != -1) {
                if (previous == '*' && ch == '/') return;
                previous = ch;
            }
            throw new IOException("Unterminated SQL comment");
        }
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
