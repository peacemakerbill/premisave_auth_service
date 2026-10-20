package com.premisave.auth.service.storage;

import com.premisave.auth.config.StorageProperties;
import com.premisave.auth.config.StorageProperties.Type;
import com.premisave.auth.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Stores application documents on a local disk or on a mounted network share.
 *
 * Replaces the Google Drive service with the same shape: a folder per application and files
 * inside it. Callers only ever see "keys" such as "HOA-2026-0007 Jane Doe/ID_FRONT_20260105.jpg";
 * a key is never a full path, and no path of this server is ever sent to a client.
 *
 * Safety rules:
 *   - every key and name is checked, and the final path must stay inside the storage directory
 *   - symbolic links are never followed outside the storage directory
 *   - files are written to a temporary name and then renamed, so a crash never leaves a half file
 *   - nothing is overwritten: a name collision gets a random suffix
 *   - an unavailable location (share not mounted, directory missing or not writable, disk full)
 *     answers 503 with a generic message; the technical reason goes to the log without any secret
 */
@Slf4j
@Service
public class FileStorageService {

    /** A folder created for one application. The id is the folder name inside the storage directory. */
    public record StoredFolder(String id) {
    }

    /** A stored file. The key is "folder/file". */
    public record StoredFile(String key, String name, String mimeType, long sizeBytes) {
    }

    /** Result of an availability check. The message contains no path and no secret. */
    public record Status(boolean available, String message) {
    }

    private static final long MAX_READ_BYTES = 64L * 1024 * 1024;
    private static final long STATUS_CACHE_MS = 5_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StorageProperties properties;
    private final List<String> configProblems;
    private final Path base;

    private volatile Status lastStatus;
    private volatile long lastStatusAt;

    public FileStorageService(StorageProperties properties) {
        this.properties = properties;
        this.configProblems = StorageLayout.validate(properties);
        Path resolved = null;
        if (configProblems.isEmpty()) {
            try {
                resolved = StorageLayout.baseDirectory(properties);
            } catch (RuntimeException e) {
                log.error("Storage location could not be resolved: {}", e.getClass().getSimpleName());
            }
        }
        this.base = resolved;
    }

    // ------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------

    /** True when the configuration is valid and the location can be used right now. */
    public boolean isAvailable() {
        return status().available();
    }

    /** Configuration problems in words, empty when valid. Safe to log. */
    public List<String> configurationProblems() {
        return configProblems;
    }

    public Type type() {
        return properties.getType();
    }

    /** The base directory, for the startup log only. Never put this in a response. */
    public Path baseDirectory() {
        return base;
    }

    /** Checks the location (cached for a few seconds, because it runs before every operation). */
    public Status status() {
        long now = System.currentTimeMillis();
        Status cached = lastStatus;
        if (cached != null && now - lastStatusAt < STATUS_CACHE_MS) {
            return cached;
        }
        Status fresh = check(true);
        lastStatus = fresh;
        lastStatusAt = now;
        return fresh;
    }

    /**
     * Runs the checks now.
     *
     * @param createIfMissing create the storage directory when it does not exist and creation is enabled
     */
    public Status check(boolean createIfMissing) {
        if (!configProblems.isEmpty()) {
            return new Status(false, "Storage configuration is invalid: " + String.join("; ", configProblems));
        }
        if (base == null) {
            return new Status(false, "Storage location could not be resolved.");
        }
        try {
            if (properties.getType() == Type.NETWORK && properties.getNetwork().isRequireMount() && !isMounted()) {
                return new Status(false, "The network share is not mounted at the configured mount path.");
            }
            if (!Files.exists(base)) {
                if (!createIfMissing || !properties.isCreateDirectories()) {
                    return new Status(false, "The storage directory does not exist.");
                }
                Files.createDirectories(base);
                log.info("Created storage directory.");
            }
            if (!Files.isDirectory(base)) {
                return new Status(false, "The storage location is not a directory.");
            }
            if (!Files.isWritable(base) || !Files.isReadable(base)) {
                return new Status(false, "The storage directory is not readable and writable by the service.");
            }
            int minFreeMb = properties.getMinFreeMb();
            if (minFreeMb > 0) {
                long usable = Files.getFileStore(base).getUsableSpace();
                if (usable < minFreeMb * 1024L * 1024L) {
                    return new Status(false, "The storage location is almost full.");
                }
            }
            return new Status(true, "Storage is available.");
        } catch (IOException | RuntimeException e) {
            log.warn("Storage check failed: {}", e.getClass().getSimpleName());
            return new Status(false, "The storage location could not be checked.");
        }
    }

    private boolean isMounted() {
        StorageProperties.Network n = properties.getNetwork();
        Path mount = StorageLayout.mountPoint(properties);
        List<String> lines = StorageLayout.readProcMounts();
        if (lines != null) {
            return StorageLayout.isMounted(lines, mount, n.getProtocol(), n.getHost(), n.getShare());
        }
        // No /proc/mounts (not Linux): a mounted share sits on a different file system than its parent.
        try {
            Path parent = mount.getParent();
            if (parent == null || !Files.isDirectory(mount)) {
                return false;
            }
            return !Files.getFileStore(mount).equals(Files.getFileStore(parent));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    /** Creates (or reuses) the folder of one application. The same name always yields the same folder. */
    public StoredFolder createApplicationFolder(String folderName) {
        requireAvailable();
        String name = StorageLayout.sanitizeName(folderName, "application");
        try {
            Path folder = StorageLayout.resolveKey(base, name);
            Files.createDirectories(folder);
            requireInside(folder);
            return new StoredFolder(name);
        } catch (IOException | IllegalArgumentException e) {
            throw failure("create the application folder", e);
        }
    }

    /** Stores the bytes as a new file inside an application folder and returns its key. */
    public StoredFile upload(String folderId, String fileName, String mimeType, byte[] content) {
        requireAvailable();
        if (content == null) {
            throw ApiException.badRequest("The file is empty.");
        }
        String name = StorageLayout.sanitizeName(fileName, "file");
        Path temp = null;
        try {
            Path folder = StorageLayout.resolveKey(base, folderId);
            if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                throw ApiException.notFound("The application folder could not be found in document storage.");
            }
            requireInside(folder);

            temp = folder.resolve(".upload-" + HexFormat.of().formatHex(randomBytes(8)) + ".tmp");
            Files.write(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictPermissions(temp);

            Path target = folder.resolve(name);
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.move(temp, target); // no REPLACE_EXISTING: an existing file is never overwritten
                    break;
                } catch (FileAlreadyExistsException e) {
                    if (attempt >= 5) {
                        throw e;
                    }
                    target = folder.resolve(withSuffix(name, HexFormat.of().formatHex(randomBytes(3))));
                }
            }
            return new StoredFile(folderId + "/" + target.getFileName(), target.getFileName().toString(), mimeType, content.length);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw failure("store the file", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /** Reads a file. A key that does not exist is a 404. */
    public byte[] download(String key) {
        requireAvailable();
        if (!StorageLayout.isWellFormedKey(key) || !key.contains("/")) {
            throw ApiException.notFound("The file could not be found in document storage.");
        }
        try {
            Path file = StorageLayout.resolveKey(base, key);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw ApiException.notFound("The file could not be found in document storage.");
            }
            requireInside(file);
            if (Files.size(file) > MAX_READ_BYTES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The file is too large to be served.");
            }
            return Files.readAllBytes(file);
        } catch (ApiException e) {
            throw e;
        } catch (NoSuchFileException e) {
            throw ApiException.notFound("The file could not be found in document storage.");
        } catch (IOException | IllegalArgumentException e) {
            throw failure("read the file", e);
        }
    }

    /**
     * Deletes a file, or a whole application folder when the key has no slash. A missing item is not
     * an error, and a storage problem is only logged: deleting must never block the caller's own work.
     */
    public void delete(String key) {
        if (!StorageLayout.isWellFormedKey(key)) {
            return;
        }
        if (!status().available()) {
            log.warn("Could not delete a stored item because storage is unavailable.");
            return;
        }
        try {
            Path target = StorageLayout.resolveKey(base, key);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            requireInside(target);
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(target);
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException | IllegalArgumentException | ApiException e) {
            log.warn("Could not delete a stored item: {}", e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void requireAvailable() {
        Status status = status();
        if (!status.available()) {
            log.warn("Document storage unavailable: {}", status.message());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Document storage is temporarily unavailable. Please try again later.");
        }
    }

    /** After symbolic links are resolved, the item must still be inside the storage directory. */
    private void requireInside(Path path) throws IOException {
        Path realBase = base.toRealPath();
        Path real = path.toRealPath();
        if (!real.startsWith(realBase)) {
            log.error("A storage path resolved outside the storage directory and was refused.");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Document storage is temporarily unavailable. Please try again later.");
        }
    }

    private ApiException failure(String action, Exception e) {
        // Class name and a generic reason only: messages can contain server paths.
        log.error("Document storage could not {}: {}", action, e.getClass().getSimpleName());
        lastStatus = null;
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Document storage is temporarily unavailable. Please try again later.");
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file); // a symbolic link is removed itself, never followed
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows or a share without POSIX permissions; the mount options decide there
        }
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // the temporary file will be cleaned up by the next restart or by hand
            }
        }
    }

    private static String withSuffix(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name + "-" + suffix;
        }
        return name.substring(0, dot) + "-" + suffix + name.substring(dot);
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}