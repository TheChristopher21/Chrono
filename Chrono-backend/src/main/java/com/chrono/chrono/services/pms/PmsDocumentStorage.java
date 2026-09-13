package com.chrono.chrono.services.pms;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Immutable blobs on a private shared volume; no client filename becomes a filesystem path. */
final class PmsDocumentStorage {
    record Stored(String key, long size, String sha256) {}
    static Stored write(Path root, Long companyId, InputStream input, long maximum) throws IOException {
        Path directory = root.toAbsolutePath().normalize().resolve(companyId.toString());
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Storage directory must not be a symbolic link");
        String key = companyId + "/" + UUID.randomUUID() + ".blob";
        Path target = resolve(root, key);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        long count = 0;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[16_384]; int read;
            while ((read = input.read(buffer)) >= 0) {
                count += read;
                if (count > maximum) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Datei überschreitet das Speicherlimit.");
                digest.update(buffer, 0, read); output.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException error) { Files.deleteIfExists(target); throw error; }
        return new Stored(key, count, HexFormat.of().formatHex(digest.digest()));
    }
    static Path resolve(Path root, String key) throws IOException {
        if (key == null || !key.matches("[0-9]+/[0-9a-f-]{36}\\.blob")) throw new IOException("Invalid storage key");
        Path normalized = root.toAbsolutePath().normalize(); Path target = normalized.resolve(key).normalize();
        if (!target.startsWith(normalized) || Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Invalid storage path");
        return target;
    }
    private PmsDocumentStorage() {}
}
