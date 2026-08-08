package com.chrono.chrono.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "app.backup.enabled", havingValue = "true")
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path dumpBinary;
    private final Path targetDirectory;
    private final String databaseUser;
    private final String databasePassword;
    private final String databaseName;
    private final Duration retention;

    public BackupService(
            @Value("${app.backup.mysqldump-bin}") String dumpBinary,
            @Value("${app.backup.directory}") String targetDirectory,
            @Value("${MYSQL_USER:root}") String databaseUser,
            @Value("${MYSQL_PASSWORD:}") String databasePassword,
            @Value("${MYSQL_DATABASE:chrono_db}") String databaseName,
            @Value("${app.backup.retention:PT720H}") Duration retention) {
        this.dumpBinary = Path.of(dumpBinary).toAbsolutePath().normalize();
        this.targetDirectory = Path.of(targetDirectory).toAbsolutePath().normalize();
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;
        this.databaseName = databaseName;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.backup.cron}")
    public void backupDatabase() {
        if (!Files.isRegularFile(dumpBinary)) {
            log.error("Datenbanksicherung übersprungen: mysqldump wurde nicht gefunden.");
            return;
        }
        try {
            Files.createDirectories(targetDirectory);
            Path output = targetDirectory.resolve(
                    "backup_" + LocalDateTime.now().format(FILE_TIME) + ".sql").normalize();
            if (!output.startsWith(targetDirectory)) {
                throw new IllegalStateException("Ungültiger Backup-Zielpfad.");
            }
            List<String> command = List.of(
                    dumpBinary.toString(),
                    "--user=" + databaseUser,
                    "--single-transaction",
                    "--routines",
                    "--events",
                    "--triggers",
                    "--result-file=" + output,
                    databaseName);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            // Keep the password out of the operating system's process argument list.
            processBuilder.environment().put("MYSQL_PWD", databasePassword);
            int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                log.error("Datenbanksicherung fehlgeschlagen (Exit-Code {}).", exitCode);
                return;
            }
            String checksum = sha256(output);
            Files.writeString(
                    output.resolveSibling(output.getFileName() + ".sha256"),
                    checksum + "  " + output.getFileName() + System.lineSeparator());
            cleanupExpiredBackups(Instant.now());
            log.info("Datenbanksicherung und SHA-256-Prüfsumme wurden erstellt: {}", output.getFileName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Datenbanksicherung wurde unterbrochen.", exception);
        } catch (IOException | RuntimeException exception) {
            log.error("Datenbanksicherung ist fehlgeschlagen.", exception);
        }
    }

    void cleanupExpiredBackups(Instant now) throws IOException {
        if (!Files.isDirectory(targetDirectory)) {
            return;
        }
        Instant cutoff = now.minus(retention);
        try (Stream<Path> files = Files.list(targetDirectory)) {
            for (Path candidate : files.filter(Files::isRegularFile).toList()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                String name = normalized.getFileName().toString();
                if (!normalized.startsWith(targetDirectory)
                        || !name.startsWith("backup_")
                        || !(name.endsWith(".sql") || name.endsWith(".sql.sha256"))) {
                    continue;
                }
                if (Files.getLastModifiedTime(normalized).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(normalized);
                    log.info("Abgelaufenes Backup-Artefakt entfernt: {}", normalized.getFileName());
                }
            }
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
}
