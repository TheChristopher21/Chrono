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
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PmsBackupVerifierTest {
    private static final String FOUNDATION = """
            CREATE TABLE pms_properties (id BIGINT);
            CREATE TABLE pms_reservations (id BIGINT);
            CREATE TABLE pms_audit_events (id BIGINT);
            CREATE TABLE pms_integration_outbox (id BIGINT);
            """;
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
    void prefersBackupConfirmedByDedicatedServiceMarker() throws Exception {
        Path confirmedBackup = directory.resolve("chrono_db_20260728T234500Z.sql");
        String sql = """
                CREATE TABLE pms_properties (id BIGINT);
                CREATE TABLE pms_reservations (id BIGINT);
                CREATE TABLE pms_audit_events (id BIGINT);
                CREATE TABLE pms_integration_outbox (id BIGINT);
                """;
        Files.writeString(confirmedBackup, sql.repeat(8));
        Files.writeString(
                confirmedBackup.resolveSibling(confirmedBackup.getFileName() + ".sha256"),
                sha256(Files.readAllBytes(confirmedBackup)) + "  " + confirmedBackup.getFileName());
        Files.writeString(directory.resolve("latest.ok"), confirmedBackup.toString());

        Path newerUnconfirmedExport = directory.resolve("manual-export.sql");
        Files.writeString(newerUnconfirmedExport, "CREATE TABLE unrelated_table (id BIGINT);".repeat(10));
        Files.setLastModifiedTime(
                newerUnconfirmedExport,
                java.nio.file.attribute.FileTime.from(Instant.now().plusSeconds(10)));

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

    @Test
    void discoversGzipDumpAndChecksTheCompressedArtifact() throws Exception {
        Path backup = writeBackup("chrono_db_current.sql.gz", FOUNDATION);

        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.OK);
        assertThat(verifier().latestVerifiedBackup()).contains(backup);

        Files.writeString(directory.resolve("chrono_db_current.sql.gz.sha256"),
                sha256(FOUNDATION.getBytes(StandardCharsets.UTF_8)));
        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.WARNING);
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
    }

    @Test
    void acceptsGzipBackupConfirmedByRelativeMarker() throws Exception {
        Path backup = writeBackup("chrono_db_current.sql.gz", FOUNDATION);
        Files.writeString(directory.resolve("latest.ok"), backup.getFileName().toString());
        Files.writeString(directory.resolve("newer.sql"), "incomplete export");
        Files.setLastModifiedTime(directory.resolve("newer.sql"),
                java.nio.file.attribute.FileTime.from(Instant.now().plusSeconds(20)));

        assertThat(verifier().latestVerifiedBackup()).contains(backup);
    }

    @Test
    void doesNotReplaceBrokenMarkedBackupWithNewerUnconfirmedGzipOrPredeployDump() throws Exception {
        Path old = writeBackup("chrono_db_old.sql", "CREATE TABLE users (id BIGINT);");
        Files.writeString(directory.resolve("latest.ok"), old.getFileName().toString());
        writeBackup("chrono_db_new.sql.gz", FOUNDATION);
        writeBackup("predeploy-new.sql.gz", FOUNDATION);

        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier().inspect().summary()).contains("chrono_db_old.sql", "pms_properties");
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
    }

    @Test
    void excludesPredeployAndUnfinishedArtifactsFromRoutineBackupSelection() throws Exception {
        writeBackup("predeploy-current.sql.gz", FOUNDATION);
        writeBackup(".chrono_current.sql", FOUNDATION);
        Files.writeString(directory.resolve("chrono_current.sql.tmp"), FOUNDATION);
        Files.writeString(directory.resolve("chrono_current.sql.gz.partial"), FOUNDATION);

        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
        Files.writeString(directory.resolve("latest.ok"), "predeploy-current.sql.gz");
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
    }

    @Test
    void refusesInvalidOrOversizedMarkerWithoutFallingBackToOtherFiles() throws Exception {
        writeBackup("valid.sql", FOUNDATION);
        Path marker = directory.resolve("latest.ok");
        Files.writeString(marker, "../outside.sql");
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
        Files.writeString(marker, " ");
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
        Files.writeString(marker, "x".repeat(4097));
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
    }

    @Test
    void recognizesRealMultilineQuotedAndQualifiedTableDefinitions() throws Exception {
        Path backup = writeBackup("current.sql", """
                -- MySQL dump header
                CREATE /* comment between keywords */ TABLE IF NOT EXISTS `pms_properties` (id BIGINT);
                create table `chrono`.`pms_reservations` (id BIGINT);
                CREATE TABLE
                  pms_audit_events (id BIGINT);
                CREATE TABLE `pms_integration_outbox` (id BIGINT);
                """);

        assertThat(verifier().verify(backup, Instant.now()).status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void ignoresNamesInCommentsInsertsAndSimilarTableNames() throws Exception {
        Path backup = writeBackup("current.sql", """
                -- CREATE TABLE pms_properties (id BIGINT);
                # CREATE TABLE pms_reservations (id BIGINT);
                /* CREATE TABLE pms_audit_events (id BIGINT); */
                INSERT INTO log VALUES ('CREATE TABLE pms_integration_outbox (id BIGINT);');
                CREATE TABLE pms_properties_history (id BIGINT);
                CREATE TEMPORARY TABLE pms_properties (id BIGINT);
                """);

        var check = verifier().verify(backup, Instant.now());
        assertThat(check.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(check.summary()).contains("pms_properties", "pms_reservations",
                "pms_audit_events", "pms_integration_outbox");
    }

    @Test
    void readsPastFoundationAndLargeInsertLinesToValidateGzipTrailer() throws Exception {
        Path backup = writeBackup("large.sql.gz", FOUNDATION
                + "INSERT INTO pms_audit_events VALUES ('" + "x".repeat(2_000_000) + "');\n");
        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.OK);

        byte[] compressed = Files.readAllBytes(backup);
        compressed[compressed.length - 8] ^= 1; // Corrupt the CRC, then checksum that exact corrupt artifact.
        Files.write(backup, compressed);
        Files.writeString(directory.resolve("large.sql.gz.sha256"), sha256(compressed));
        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier().inspect().summary()).contains("beschädigt");
        assertThat(verifier().latestVerifiedBackup()).isEmpty();
    }

    @Test
    void findsFoundationAfterMultiMegabyteInsertCrossingTokenizerBuffers() throws Exception {
        writeBackup("large.sql.gz", "INSERT INTO legacy_log VALUES ('" + "x".repeat(2_000_000)
                + "\\'CREATE TABLE ignored (id BIGINT);');\n" + FOUNDATION);

        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void rejectsTruncatedGzipAndOversizedChecksum() throws Exception {
        Path backup = writeBackup("current.sql.gz", FOUNDATION);
        Files.writeString(directory.resolve("current.sql.gz.sha256"), "a".repeat(4097));
        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.WARNING);
        byte[] complete = Files.readAllBytes(backup);
        Files.write(backup, java.util.Arrays.copyOf(complete, complete.length - 4));
        assertThat(verifier().inspect().status()).isEqualTo(HealthStatus.CRITICAL);
    }

    @Test
    void rejectsSymlinkArtifactInMarkerAndDirectoryScan() throws Exception {
        Path outside = Files.createTempFile("pms-backup-outside", ".sql");
        try {
            Files.writeString(outside, FOUNDATION);
            Path link = directory.resolve("linked.sql");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.io.IOException exception) {
                org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable on this host");
            }
            assertThat(verifier().latestVerifiedBackup()).isEmpty();
            assertThat(verifier().verify(link, Instant.now()).status()).isEqualTo(HealthStatus.CRITICAL);
            Files.writeString(directory.resolve("latest.ok"), "linked.sql");
            assertThat(verifier().latestVerifiedBackup()).isEmpty();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void sharesOneContentScanBetweenHealthAndRestoreLookups() throws Exception {
        writeBackup("current.sql.gz", FOUNDATION);
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26));

        assertThat(verifier.inspect().status()).isEqualTo(HealthStatus.OK);
        assertThat(verifier.latestVerifiedBackup()).isPresent();
        assertThat(verifier.inspect().status()).isEqualTo(HealthStatus.OK);
        assertThat(verifier.scans).isEqualTo(1);
    }

    @Test
    void invalidatesCacheWhenArtifactChanges() throws Exception {
        Path backup = writeBackup("current.sql", FOUNDATION);
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26));
        Instant now = Instant.now();
        assertThat(verifier.verify(backup, now).status()).isEqualTo(HealthStatus.OK);

        Files.writeString(backup, "CREATE TABLE users (id BIGINT);");
        assertThat(verifier.verify(backup, now.plusSeconds(1)).status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier.scans).isEqualTo(2);
    }

    @Test
    void checksumContentsInvalidateCacheEvenWhenSizeAndTimestampArePreserved() throws Exception {
        Path backup = writeBackup("current.sql", FOUNDATION);
        Path checksum = directory.resolve("current.sql.sha256");
        String original = Files.readString(checksum);
        var timestamp = Files.getLastModifiedTime(checksum);
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26));
        Instant now = Instant.now();
        assertThat(verifier.verify(backup, now).status()).isEqualTo(HealthStatus.OK);

        Files.writeString(checksum, "0".repeat(64) + original.substring(64));
        Files.setLastModifiedTime(checksum, timestamp);
        assertThat(verifier.verify(backup, now.plusSeconds(1)).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(verifier.scans).isEqualTo(2);
        Files.delete(checksum);
        assertThat(verifier.verify(backup, now.plusSeconds(2)).checksumValid()).isFalse();
        assertThat(verifier.scans).isEqualTo(3);
        Files.writeString(checksum, original);
        assertThat(verifier.verify(backup, now.plusSeconds(3)).status()).isEqualTo(HealthStatus.OK);
        assertThat(verifier.scans).isEqualTo(4);
    }

    @Test
    void markerChangesInvalidateTheSingleEntryCache() throws Exception {
        Path first = writeBackup("first.sql", FOUNDATION);
        Path second = writeBackup("second.sql.gz", FOUNDATION);
        Path marker = directory.resolve("latest.ok");
        Files.writeString(marker, first.getFileName().toString());
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26));
        assertThat(verifier.latestVerifiedBackup()).contains(first);
        Files.writeString(marker, first.getFileName() + "\n");
        assertThat(verifier.latestVerifiedBackup()).contains(first);
        assertThat(verifier.scans).isEqualTo(2);

        Files.writeString(marker, second.getFileName().toString());
        assertThat(verifier.latestVerifiedBackup()).contains(second);
        Files.writeString(marker, first.getFileName().toString());
        assertThat(verifier.latestVerifiedBackup()).contains(first);
        assertThat(verifier.scans).isEqualTo(4);
    }

    @Test
    void expiryForcesAFullScanEvenIfArtifactMetadataDidNotChange() throws Exception {
        Path backup = writeBackup("current.sql", FOUNDATION);
        var timestamp = Files.getLastModifiedTime(backup);
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26));
        Instant now = Instant.now();
        assertThat(verifier.verify(backup, now).status()).isEqualTo(HealthStatus.OK);
        Files.writeString(backup, FOUNDATION.replace("pms_properties", "pms_propertiez"));
        Files.setLastModifiedTime(backup, timestamp);

        assertThat(verifier.verify(backup, now.plusSeconds(61)).status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier.scans).isEqualTo(2);
    }

    @Test
    void recomputesAgeWithoutRescanningContents() throws Exception {
        Path backup = writeBackup("current.sql", FOUNDATION);
        Instant now = Instant.now();
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.from(now.minusSeconds(15)));
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofSeconds(20));
        assertThat(verifier.verify(backup, now).status()).isEqualTo(HealthStatus.OK);

        assertThat(verifier.verify(backup, now.plusSeconds(10)).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(verifier.verify(backup, now.plusSeconds(10)).checksumValid()).isTrue();
        assertThat(verifier.scans).isEqualTo(1);
    }

    @Test
    void changingBackupDuringScanIsNeverTrustedOrCached() throws Exception {
        Path backup = writeBackup("current.sql", FOUNDATION);
        CountingVerifier verifier = new CountingVerifier(directory, Duration.ofHours(26)) {
            @Override
            java.util.Set<String> missingRestoreFoundation(Path path) throws java.io.IOException {
                var missing = super.missingRestoreFoundation(path);
                if (scans == 1) Files.writeString(path, FOUNDATION + "\n");
                return missing;
            }
        };
        Instant now = Instant.now();
        assertThat(verifier.verify(backup, now).status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(verifier.verify(backup, now.plusSeconds(1)).status()).isEqualTo(HealthStatus.WARNING);
        assertThat(verifier.scans).isEqualTo(2);
    }

    private static class CountingVerifier extends PmsBackupVerifier {
        int scans;

        CountingVerifier(Path directory, Duration maxAge) {
            super(true, directory.toString(), maxAge, 1);
        }

        @Override
        java.util.Set<String> missingRestoreFoundation(Path backup) throws java.io.IOException {
            scans++;
            return super.missingRestoreFoundation(backup);
        }
    }

    private PmsBackupVerifier verifier() {
        return new PmsBackupVerifier(true, directory.toString(), Duration.ofHours(26), 1);
    }

    private Path writeBackup(String name, String sql) throws Exception {
        Path backup = directory.resolve(name);
        if (name.endsWith(".gz")) {
            try (var gzip = new GZIPOutputStream(Files.newOutputStream(backup))) {
                gzip.write(sql.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            Files.writeString(backup, sql);
        }
        Files.writeString(backup.resolveSibling(backup.getFileName() + ".sha256"),
                sha256(Files.readAllBytes(backup)) + "  " + backup.getFileName());
        return backup;
    }

    private String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(data));
    }
}
