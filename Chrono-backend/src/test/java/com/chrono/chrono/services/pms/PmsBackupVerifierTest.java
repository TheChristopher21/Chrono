package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PmsBackupVerifierTest {
    @TempDir
    Path directory;

    @Test
    void acceptsRecentChecksumProtectedRestoreFoundation() throws Exception {
        Path backup = directory.resolve("backup_2026-07-28_23-45-00.sql");
        String sql = """
                CREATE TABLE pms_properties (id BIGINT);
                CREATE TABLE pms_reservations (id BIGINT);
                CREATE TABLE pms_audit_events (id BIGINT);
                CREATE TABLE pms_integration_outbox (id BIGINT);
                INSERT INTO pms_properties VALUES (1);
                """;
        Files.writeString(backup, sql.repeat(8));
        Files.writeString(
                backup.resolveSibling(backup.getFileName() + ".sha256"),
                sha256(Files.readAllBytes(backup)) + "  " + backup.getFileName());
        PmsBackupVerifier verifier =
                new PmsBackupVerifier(true, directory.toString(), Duration.ofHours(26), 100);

        PmsBackupVerifier.BackupCheck result = verifier.verify(backup, Instant.now());

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.checksumValid()).isTrue();
        assertThat(result.latestBackupAt()).isNotNull();
    }

    @Test
    void rejectsBackupThatCannotRestorePmsFoundation() throws Exception {
        Path backup = directory.resolve("backup_2026-07-28_23-45-00.sql");
        Files.writeString(backup, "CREATE TABLE unrelated_table (id BIGINT);".repeat(10));
        PmsBackupVerifier verifier =
                new PmsBackupVerifier(true, directory.toString(), Duration.ofHours(26), 100);

        PmsBackupVerifier.BackupCheck result = verifier.verify(backup, Instant.now());

        assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(result.summary()).contains("Kerntabellen");
    }

    @Test
    void discoversDedicatedBackupServiceFileName() throws Exception {
        Path backup = directory.resolve("chrono_db_20260728T234500Z.sql");
        String sql = """
                CREATE TABLE pms_properties (id BIGINT);
                CREATE TABLE pms_reservations (id BIGINT);
                CREATE TABLE pms_audit_events (id BIGINT);
                CREATE TABLE pms_integration_outbox (id BIGINT);
                """;
        Files.writeString(backup, sql.repeat(8));
        Files.writeString(
                backup.resolveSibling(backup.getFileName() + ".sha256"),
                sha256(Files.readAllBytes(backup)) + "  " + backup.getFileName());
        PmsBackupVerifier verifier =
                new PmsBackupVerifier(true, directory.toString(), Duration.ofHours(26), 100);

        assertThat(verifier.inspect().status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void reportsDisabledBackupHonestly() {
        PmsBackupVerifier verifier =
                new PmsBackupVerifier(false, directory.toString(), Duration.ofHours(26), 100);

        assertThat(verifier.inspect().status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
    }

    private String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(new String(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)));
    }
}
