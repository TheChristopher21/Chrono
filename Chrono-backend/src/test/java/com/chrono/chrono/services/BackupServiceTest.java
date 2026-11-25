package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceTest {
    @TempDir
    Path directory;

    @Test
    void removesOnlyExpiredChronoBackupArtifacts() throws Exception {
        Instant now = Instant.now();
        Path oldBackup = Files.writeString(directory.resolve("backup_old.sql"), "old");
        Path oldChecksum = Files.writeString(directory.resolve("backup_old.sql.sha256"), "old");
        Path currentBackup = Files.writeString(directory.resolve("backup_current.sql"), "current");
        Path unrelated = Files.writeString(directory.resolve("important.sql"), "keep");
        Files.setLastModifiedTime(oldBackup, FileTime.from(now.minus(Duration.ofDays(31))));
        Files.setLastModifiedTime(oldChecksum, FileTime.from(now.minus(Duration.ofDays(31))));

        BackupService service = new BackupService(
                directory.resolve("mysqldump").toString(), directory.toString(),
                "user", "secret", "chrono", Duration.ofDays(30));
        service.cleanupExpiredBackups(now);

        assertThat(oldBackup).doesNotExist();
        assertThat(oldChecksum).doesNotExist();
        assertThat(currentBackup).exists();
        assertThat(unrelated).exists();
    }
}
