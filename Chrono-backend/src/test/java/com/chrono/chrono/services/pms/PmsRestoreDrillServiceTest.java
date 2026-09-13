package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PmsRestoreDrillServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsRestoreDrillAsNotConfiguredWhenDisabled() {
        PmsBackupVerifier verifier = mock(PmsBackupVerifier.class);
        PmsRestoreDrillService service = service(verifier, false);

        assertThat(service.inspect().status()).isEqualTo(HealthStatus.NOT_CONFIGURED);
        verifyNoInteractions(verifier);
    }

    @Test
    void acceptsFreshMarkerForLatestVerifiedBackup() throws Exception {
        Path backup = tempDirectory.resolve("backup_20260728_200000.sql");
        Files.writeString(backup, "-- verified backup");
        Files.writeString(
                tempDirectory.resolve("restore-verification.txt"),
                "OK|2026-07-28T20:00:00|" + backup.getFileName() + "|Restore erfolgreich.\n");
        PmsBackupVerifier verifier = mock(PmsBackupVerifier.class);
        when(verifier.latestVerifiedBackup()).thenReturn(Optional.of(backup));

        PmsRestoreDrillService.RestoreCheck result = service(verifier, true).inspect();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.verifiedAt()).isNotNull();
    }

    @Test
    void warnsWhenMarkerBelongsToOlderBackup() throws Exception {
        Path latest = tempDirectory.resolve("backup_20260728_210000.sql");
        Files.writeString(latest, "-- latest backup");
        Files.writeString(
                tempDirectory.resolve("restore-verification.txt"),
                "OK|2026-07-28T20:00:00|backup_20260728_200000.sql|Restore erfolgreich.\n");
        PmsBackupVerifier verifier = mock(PmsBackupVerifier.class);
        when(verifier.latestVerifiedBackup()).thenReturn(Optional.of(latest));

        assertThat(service(verifier, true).inspect().status()).isEqualTo(HealthStatus.WARNING);
    }

    private PmsRestoreDrillService service(PmsBackupVerifier verifier, boolean enabled) {
        return new PmsRestoreDrillService(
                verifier,
                enabled,
                tempDirectory.resolve("mysql").toString(),
                "localhost",
                3306,
                "restore-user",
                "secret",
                Duration.ofDays(7));
    }

    @Test
    void externalProofWorksWithoutMysqlBinaryAndMustMatchBackupAndRelease() throws Exception {
        Path backup = tempDirectory.resolve("current.sql");
        Files.writeString(backup, "-- verified backup");
        String checksum = "a".repeat(64);
        Files.writeString(tempDirectory.resolve("current.sql.sha256"), checksum + "  current.sql\n");
        var proof = new ObjectMapper().createObjectNode().put("schemaVersion", 1).put("status", "OK")
                .put("verifiedAt", Instant.now().toString()).put("backupFile", "current.sql")
                .put("backupSha256", checksum).put("flywayVersion", PmsRestoreDrillService.expectedSchemaVersion());
        Path evidence = tempDirectory.resolve("restore-verification.json");
        Files.writeString(evidence, proof.toString());
        var verifier = mock(PmsBackupVerifier.class);
        when(verifier.latestVerifiedBackup()).thenReturn(Optional.of(backup));
        var service = externalService(verifier);
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.OK);
        proof.put("backupSha256", "b".repeat(64)); Files.writeString(evidence, proof.toString());
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.CRITICAL);
        proof.put("backupSha256", checksum).put("flywayVersion", "17"); Files.writeString(evidence, proof.toString());
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.WARNING);
    }

    @Test
    void externalProofRejectsStaleFailedAndMalformedEvidence() throws Exception {
        Path backup = tempDirectory.resolve("current.sql"); Files.writeString(backup, "backup");
        var verifier = mock(PmsBackupVerifier.class); when(verifier.latestVerifiedBackup()).thenReturn(Optional.of(backup));
        var service = externalService(verifier);
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.WARNING);
        Path evidence = tempDirectory.resolve("restore-verification.json");
        Files.writeString(evidence, "{\"schemaVersion\":1,\"status\":\"FAILED\"}");
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.CRITICAL);
        Files.writeString(evidence, "{\"schemaVersion\":1,\"status\":\"OK\",\"verifiedAt\":\"" + Instant.now().minus(Duration.ofDays(9)) + "\"}");
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.WARNING);
        Files.writeString(evidence, "not json");
        assertThat(service.inspect().status()).isEqualTo(HealthStatus.CRITICAL);
    }
    private PmsRestoreDrillService externalService(PmsBackupVerifier verifier) {
        return new PmsRestoreDrillService(verifier, false, "missing-mysql", "localhost", 3306, "user", "secret", Duration.ofDays(7), true, tempDirectory.toString());
    }
}
