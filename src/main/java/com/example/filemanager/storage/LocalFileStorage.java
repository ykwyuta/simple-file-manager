package com.example.filemanager.storage;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed storage for development and the end-to-end suite.
 *
 * <p>
 * Activated with the {@code local-storage} profile, which lets the whole
 * application run with nothing but a JVM — no object store to install, start or
 * clean up. Not for production: it is single-node and has no replication.
 */
@Component
@Profile("local-storage")
public class LocalFileStorage implements FileStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;
    private final boolean deleteOnShutdown;

    public LocalFileStorage(
            @Value("${storage.local.path:#{null}}") String configuredPath,
            @Value("${storage.local.delete-on-shutdown:true}") boolean deleteOnShutdown) throws IOException {
        this.root = configuredPath != null
                ? Files.createDirectories(Path.of(configuredPath))
                : Files.createTempDirectory("file-manager-storage-");
        this.deleteOnShutdown = deleteOnShutdown;
        logger.warn("Using local filesystem storage at {}. This profile is for development and tests only.", root);
    }

    @Override
    public void upload(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public byte[] download(String key) throws IOException {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (NoSuchFileException e) {
            throw new IOException("Stored object not found: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Maps a storage key to a path under {@link #root}, refusing anything that
     * would escape it. Keys are generated internally, but a storage backend
     * should not depend on its caller for containment.
     */
    private Path resolve(String key) {
        Path resolved = root.resolve(Objects.requireNonNull(key)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }

    @PreDestroy
    void cleanUp() throws IOException {
        if (!deleteOnShutdown || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.debug("Could not delete {}", path, e);
                }
            });
        }
    }
}
