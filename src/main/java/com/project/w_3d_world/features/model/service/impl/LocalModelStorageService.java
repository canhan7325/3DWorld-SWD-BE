package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import static com.project.w_3d_world.features.model.storage.ModelStorageException.Code.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/**
 * Private local storage. Configuration is validated on use, never during bean construction.
 *
 * Deployment restriction: one backend JVM exclusively owns the root; OS permissions/Windows ACLs
 * must prevent other principals/processes from mutating the root, descendants or ancestors during
 * operations. JDK path APIs on Windows cannot provide race-free directory-relative operations.
 * Component checks, NOFOLLOW_LINKS and rechecks reject existing links/junctions, but do not promise
 * protection against a privileged concurrent filesystem mutator. Do not use a shared/network root.
 * The JVM-wide monitor serializes all instances, including collision checks and atomic publication.
 * POSIX directories/files created here use 0700/0600; Windows needs a private inherited ACL.
 *
 * No fallback is used when atomic rename is unsupported. Root is never served as static content.
 */
@Service
public class LocalModelStorageService implements ModelStorageService {
    private static final Object FILE_OPERATIONS = new Object();
    private final Supplier<String> rootSetting;
    private final ModelStorageKeyFactory keys;
    private final ModelFileValidator validator = new ModelFileValidator();

    @Autowired
    public LocalModelStorageService(Environment environment) {
        this(() -> environment.getProperty("MODEL_STORAGE_ROOT"), new ModelStorageKeyFactory());
    }

    public LocalModelStorageService(Path root) {
        this(root, new ModelStorageKeyFactory());
    }

    public LocalModelStorageService(Path root, ModelStorageKeyFactory keys) {
        this(() -> root == null ? null : root.toString(), keys);
    }

    private LocalModelStorageService(Supplier<String> rootSetting, ModelStorageKeyFactory keys) {
        this.rootSetting = rootSetting;
        this.keys = Objects.requireNonNull(keys);
    }

    @Override
    public StoredModelFile store(InputStream input, String originalFilename, String declaredContentType,
            ModelFileRole role, Integer modelId, Integer modelVersionId) {
        synchronized (FILE_OPERATIONS) {
            Path temporary = null;
            Object temporaryIdentity = null;
            try {
                Path root = root(true);
                if (input == null) throw new ModelStorageException(INVALID_METADATA, "An input stream is required.");
                var format = validator.validateMetadata(originalFilename, null, role);
                String key = keys.create(modelId, modelVersionId, format.extension());
                Path destination = resolve(root, key);
                directories(destination.getParent(), true);
                ensureAbsent(destination);
                temporary = Files.createTempFile(destination.getParent(), ".upload-", ".part", permissions(false));
                temporaryIdentity = regular(temporary).fileKey();
                MessageDigest digest = sha256();
                long size = 0;
                try (var channel = FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS);
                        var output = Channels.newOutputStream(channel)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Storage interrupted.");
                        if (read == 0) {
                            int value = input.read();
                            if (value == -1) break;
                            buffer[0] = (byte) value;
                            read = 1;
                        }
                        size += read;
                        if (size > format.maxBytes()) {
                            throw new ModelStorageException(TOO_LARGE, "File exceeds its category size limit.");
                        }
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                    }
                    if (size == 0) throw new ModelStorageException(EMPTY_FILE, "Empty files are not supported.");
                    channel.force(true);
                }
                verifyIdentity(temporary, temporaryIdentity);
                if (role == ModelFileRole.EDITABLE_SCENE || role == ModelFileRole.PREVIEW) {
                    validator.validateContent(temporary, format);
                }
                directories(destination.getParent(), false);
                verifyIdentity(temporary, temporaryIdentity);
                ensureAbsent(destination);
                // With exclusive root ownership and the monitor, no other supported writer can
                // create this destination between the absence check and ATOMIC_MOVE.
                moveAtomically(temporary, destination);
                temporary = null;
                return new StoredModelFile(key, format.extension(), format.mimeType(), size,
                        HexFormat.of().formatHex(digest.digest()));
            } catch (Exception exception) {
                ModelStorageException failure = translate(exception);
                if (temporary != null) {
                    try {
                        directories(temporary.getParent(), false);
                        verifyIdentity(temporary, temporaryIdentity);
                        Files.delete(temporary);
                    } catch (Exception cleanup) {
                        failure.addSuppressed(new ModelStorageException(CLEANUP_FAILURE,
                                "Temporary file cleanup failed; internal reconciliation is required.", cleanup));
                    }
                }
                throw failure;
            }
        }
    }

    @Override
    public InputStream open(String storageKey) {
        synchronized (FILE_OPERATIONS) {
            keys.validate(storageKey);
            FileChannel channel = null;
            try {
                Path file = resolve(root(false), storageKey);
                directories(file.getParent(), false);
                Object identity = regular(file).fileKey();
                channel = FileChannel.open(file, READ, NOFOLLOW_LINKS);
                directories(file.getParent(), false);
                verifyIdentity(file, identity);
                return Channels.newInputStream(channel);
            } catch (Exception exception) {
                if (channel != null) {
                    try { channel.close(); } catch (IOException close) { exception.addSuppressed(close); }
                }
                throw translate(exception);
            }
        }
    }

    @Override
    public boolean deleteForCleanup(String storageKey) {
        synchronized (FILE_OPERATIONS) {
            keys.validate(storageKey);
            try {
                Path file = resolve(root(false), storageKey);
                directories(file.getParent(), false);
                Object identity = regular(file).fileKey();
                directories(file.getParent(), false);
                verifyIdentity(file, identity);
                Files.delete(file);
                return true;
            } catch (NoSuchFileException exception) {
                return false;
            } catch (Exception exception) {
                throw translate(exception);
            }
        }
    }

    /** Isolated hook for simulating unsupported atomic rename in unit tests. */
    protected void moveAtomically(Path source, Path destination) throws IOException {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path root(boolean create) throws IOException {
        String configured = rootSetting.get();
        if (configured == null || configured.isBlank()) {
            throw new ModelStorageException(CONFIGURATION, "MODEL_STORAGE_ROOT must be configured before storage is used.");
        }
        final Path root;
        try { root = Path.of(configured); }
        catch (InvalidPathException exception) {
            throw new ModelStorageException(CONFIGURATION, "MODEL_STORAGE_ROOT is invalid.", exception);
        }
        if (!root.isAbsolute() || !root.normalize().equals(root) || root.getParent() == null) {
            throw new ModelStorageException(CONFIGURATION, "MODEL_STORAGE_ROOT must be an absolute dedicated directory.");
        }
        directories(root, create);
        return root;
    }

    private Path resolve(Path root, String key) {
        keys.validate(key);
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw unsafe();
        return resolved;
    }

    private void directories(Path directory, boolean create) throws IOException {
        Path current = directory.getRoot();
        for (Path component : directory) {
            current = current.resolve(component);
            BasicFileAttributes attributes;
            try { attributes = attributes(current); }
            catch (NoSuchFileException missing) {
                if (!create) throw missing;
                try { Files.createDirectory(current, permissions(true)); }
                catch (FileAlreadyExistsException concurrent) { /* Validate the existing object below. */ }
                attributes = attributes(current);
            }
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                    || !current.toRealPath().equals(current.toAbsolutePath().normalize())) throw unsafe();
        }
    }

    private BasicFileAttributes regular(Path file) throws IOException {
        var attributes = attributes(file);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                || !file.toRealPath().equals(file.toAbsolutePath().normalize())) throw unsafe();
        return attributes;
    }

    private void verifyIdentity(Path file, Object expected) throws IOException {
        Object actual = regular(file).fileKey();
        if (expected != null && !expected.equals(actual)) throw unsafe();
    }

    private BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
    }

    private void ensureAbsent(Path file) throws IOException {
        try { attributes(file); }
        catch (NoSuchFileException absent) { return; }
        throw new ModelStorageException(COLLISION, "The generated storage key already exists; no file was replaced.");
    }

    private FileAttribute<?>[] permissions(boolean directory) {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))};
        }
        return new FileAttribute<?>[0];
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private static ModelStorageException unsafe() {
        return new ModelStorageException(UNSAFE_PATH, "Storage path is not a safe managed directory or regular file.");
    }

    private static ModelStorageException translate(Exception exception) {
        if (exception instanceof ModelStorageException storage) return storage;
        if (exception instanceof AtomicMoveNotSupportedException) {
            return new ModelStorageException(ATOMIC_MOVE_UNSUPPORTED, "Storage filesystem must support atomic moves.", exception);
        }
        if (exception instanceof NoSuchFileException) {
            return new ModelStorageException(NOT_FOUND, "Stored file or managed directory was not found.", exception);
        }
        if (exception instanceof FileAlreadyExistsException) {
            return new ModelStorageException(COLLISION, "Storage destination already exists.", exception);
        }
        return new ModelStorageException(IO_FAILURE, "Local storage operation failed.", exception);
    }
}
