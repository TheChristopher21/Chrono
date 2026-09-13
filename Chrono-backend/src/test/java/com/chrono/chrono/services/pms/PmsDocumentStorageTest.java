package com.chrono.chrono.services.pms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
class PmsDocumentStorageTest {
    @TempDir Path directory;
    @Test void streamsAnImmutableTenantBlobWithIndependentFingerprint() throws Exception {
        byte[] bytes = "%PDF-1.4\nContract".getBytes();
        var stored = PmsDocumentStorage.write(directory, 17L, new ByteArrayInputStream(bytes), 1024);
        assertThat(stored.key()).startsWith("17/").endsWith(".blob");
        assertThat(stored.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(Files.readAllBytes(PmsDocumentStorage.resolve(directory, stored.key()))).isEqualTo(bytes);
        assertThat(PmsDocumentStorage.write(directory, 17L, new ByteArrayInputStream(bytes), 1024).key()).isNotEqualTo(stored.key());
    }
    @Test void rejectsTraversalAndDeletesAnOversizedPartialUpload() throws Exception {
        assertThatThrownBy(() -> PmsDocumentStorage.resolve(directory, "../outside.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> PmsDocumentStorage.write(directory, 17L, new ByteArrayInputStream(new byte[2048]), 1024)).hasMessageContaining("Speicherlimit");
        try (var files = Files.list(directory.resolve("17"))) { assertThat(files.count()).isZero(); }
    }
}
