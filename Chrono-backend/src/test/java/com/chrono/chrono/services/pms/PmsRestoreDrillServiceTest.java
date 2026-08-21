package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
}
