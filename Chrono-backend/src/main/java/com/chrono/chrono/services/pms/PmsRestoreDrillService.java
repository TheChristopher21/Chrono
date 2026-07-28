package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class PmsRestoreDrillService {
    private static final String MARKER = "restore-verification.txt";
    private static final int REQUIRED_TABLES = 4;

    private final PmsBackupVerifier backupVerifier;
    private final boolean enabled;
    private final Path mysqlBinary;
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final Duration maxAge;

    public PmsRestoreDrillService(
            PmsBackupVerifier backupVerifier,
            @Value("${app.backup.restore-test.enabled:false}") boolean enabled,
            @Value("${app.backup.mysql-bin:/usr/bin/mysql}") String mysqlBinary,
            @Value("${app.backup.restore-test.host:localhost}") String host,
            @Value("${app.backup.restore-test.port:3306}") int port,
            @Value("${app.backup.restore-test.user:root}") String user,
            @Value("${app.backup.restore-test.password:}") String password,
            @Value("${app.backup.restore-test.max-age:PT168H}") Duration maxAge) {
        this.backupVerifier = backupVerifier;
        this.enabled = enabled;
        this.mysqlBinary = Path.of(mysqlBinary).toAbsolutePath().normalize();
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.maxAge = maxAge;
    }

    public RestoreCheck inspect() {
        if (!enabled) {
            return new RestoreCheck(
                    HealthStatus.NOT_CONFIGURED,
                    "Automatischer Restore-Probelauf ist nicht aktiviert.",
                    null);
        }
        Optional<Path> backup = backupVerifier.latestVerifiedBackup();
        if (backup.isEmpty()) {
            return new RestoreCheck(
                    HealthStatus.CRITICAL,
                    "Kein prüfsummengeschütztes Backup für den Restore-Probelauf vorhanden.",
                    null);
        }
        Path marker = backup.get().getParent().resolve(MARKER);
        if (!Files.isRegularFile(marker)) {
            return new RestoreCheck(
                    HealthStatus.WARNING,
                    "Für die aktuelle Sicherungsablage wurde noch kein Restore nachgewiesen.",
                    null);
        }
        try {
            LocalDateTime verifiedAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(marker).toInstant(), ZoneId.systemDefault());
            if (Files.getLastModifiedTime(marker).toInstant().isBefore(Instant.now().minus(maxAge))) {
                return new RestoreCheck(
                        HealthStatus.WARNING,
                        "Der letzte isolierte Restore-Probelauf ist zu alt.",
                        verifiedAt);
            }
            String markerContent = Files.readString(marker);
            if (!markerContent.startsWith("OK|")) {
                return new RestoreCheck(
                        HealthStatus.CRITICAL,
                        "Der letzte isolierte Restore-Probelauf ist fehlgeschlagen.",
                        verifiedAt);
            }
            if (!markerContent.contains("|" + backup.get().getFileName() + "|")) {
                return new RestoreCheck(
                        HealthStatus.WARNING,
                        "Die neueste Sicherung wurde noch nicht durch einen Restore-Probelauf bestätigt.",
                        verifiedAt);
            }
            return new RestoreCheck(
                    HealthStatus.OK,
                    "Isolierter Restore-Probelauf und Kerntabellenprüfung erfolgreich.",
                    verifiedAt);
        } catch (IOException exception) {
            return new RestoreCheck(
                    HealthStatus.CRITICAL,
                    "Restore-Nachweis konnte nicht gelesen werden.",
                    null);
        }
    }

    public RestoreCheck runDrill() {
        if (!enabled) {
            return inspect();
        }
        Optional<Path> backup = backupVerifier.latestVerifiedBackup();
        if (backup.isEmpty() || !Files.isRegularFile(mysqlBinary)) {
            return record(backup.orElse(null), false,
                    "Backup oder mysql-Client für Restore-Probelauf fehlt.");
        }
        String database = "chrono_restore_verify_" + System.currentTimeMillis();
        if (!database.matches("chrono_restore_verify_[0-9]+")) {
            throw new IllegalStateException("Unsicherer Restore-Datenbankname.");
        }
        try {
            execute(List.of("-e", "CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"), null);
            execute(List.of(database), backup.get());
            String tableCount = execute(List.of(
                    "--batch", "--skip-column-names", database, "-e",
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name IN "
                            + "('pms_properties','pms_reservations','pms_audit_events','pms_integration_outbox')"),
                    null).trim();
            if (!String.valueOf(REQUIRED_TABLES).equals(tableCount)) {
                return record(backup.get(), false,
                        "Restore enthielt nicht alle erforderlichen PMS-Kerntabellen.");
            }
            return record(backup.get(), true, "Restore erfolgreich.");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return record(backup.get(), false, "Restore-Probelauf ist fehlgeschlagen.");
        } finally {
            try {
                execute(List.of("-e", "DROP DATABASE IF EXISTS `" + database + "`"), null);
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String execute(List<String> arguments, Path input)
            throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of(
                mysqlBinary.toString(), "--protocol=TCP", "--host=" + host,
                "--port=" + port, "--user=" + user));
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("MYSQL_PWD", password);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (input != null) {
            builder.redirectInput(input.toFile());
        }
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("mysql-Client meldete Exit-Code " + exitCode);
        }
        return output;
    }

    private RestoreCheck record(Path backup, boolean success, String message) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (backup != null) {
            try {
                Files.writeString(
                        backup.getParent().resolve(MARKER),
                        (success ? "OK" : "FAILED") + "|" + now + "|" + backup.getFileName()
                                + "|" + message + System.lineSeparator());
            } catch (IOException ignored) {
                return new RestoreCheck(
                        HealthStatus.CRITICAL, "Restore-Nachweis konnte nicht gespeichert werden.", now);
            }
        }
        return new RestoreCheck(
                success ? HealthStatus.OK : HealthStatus.CRITICAL, message, now);
    }

    public record RestoreCheck(HealthStatus status, String summary, LocalDateTime verifiedAt) {
    }
}
