package dev.minicloud.files;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import dev.minicloud.config.MiniCloudProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only class that builds filesystem paths.
 *
 * <pre>
 * root/tmp/                       servlet multipart spool
 * root/quarantine/&lt;uuid&gt;.part      uploads not yet validated
 * root/files/&lt;owner&gt;/&lt;id&gt;.bin       content
 * root/files/&lt;owner&gt;/&lt;id&gt;.properties metadata; its presence commits the file
 * </pre>
 */
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
    private static final String BLOB = ".bin";
    private static final String META = ".properties";
    private static final String TMP = ".tmp";
    private static final String PART = ".part";

    private final Path root;
    private final Path quarantine;
    private final Path files;

    public FileStorage(Path configuredRoot) throws IOException {
        Files.createDirectories(configuredRoot);
        this.root = configuredRoot.toRealPath();
        this.quarantine = createPrivateDirectory(root.resolve("quarantine"));
        this.files = createPrivateDirectory(root.resolve("files"));
        Path spool = createPrivateDirectory(root.resolve("tmp"));
        if (!Files.isWritable(root)) {
            throw new IOException("Storage root is not writable");
        }
        // No uploads are in flight at startup, so leftovers are safe to remove.
        deleteChildren(quarantine);
        deleteChildren(spool);
        removeUncommitted();
    }

    public Path root() {
        return root;
    }

    public Path newQuarantinePath() {
        return resolveInside(quarantine, UUID.randomUUID() + PART);
    }

    public long usableSpace() throws IOException {
        return Files.getFileStore(root).getUsableSpace();
    }

    /**
     * Moves a quarantined file into final storage and writes its metadata last, atomically.
     */
    public void commit(Path quarantined, FileMetadata metadata) throws IOException {
        if (!quarantined.getParent().equals(quarantine)) {
            throw new IllegalArgumentException("Not a quarantined file");
        }
        Path ownerDir = createPrivateDirectory(ownerDir(metadata.owner()));
        Path blob = resolveInside(ownerDir, metadata.id() + BLOB);
        Path meta = resolveInside(ownerDir, metadata.id() + META);
        Path metaTmp = resolveInside(ownerDir, metadata.id() + META + TMP);

        Files.move(quarantined, blob, StandardCopyOption.ATOMIC_MOVE);
        try {
            try (FileChannel channel = FileChannel.open(metaTmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                 Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8)) {
                metadata.toProperties().store(writer, null);
                writer.flush();
                channel.force(true);
            }
            Files.move(metaTmp, meta, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(metaTmp);
            Files.deleteIfExists(blob);
            throw e;
        }
    }

    public List<FileMetadata> list(String owner) throws IOException {
        Path ownerDir = ownerDir(owner);
        if (!Files.isDirectory(ownerDir, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<FileMetadata> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ownerDir, "*" + META)) {
            for (Path meta : stream) {
                try {
                    FileMetadata metadata = readMetadata(meta);
                    if (metadata.owner().equals(owner)) {
                        result.add(metadata);
                    }
                } catch (IllegalArgumentException | IOException e) {
                    log.warn("Skipping unreadable metadata entry for owner {}", owner);
                }
            }
        }
        result.sort(Comparator.comparing(FileMetadata::uploadedAt).reversed());
        return result;
    }

    /**
     * @throws StoredFileNotFoundException if the file is missing or not owned by {@code owner}
     */
    public Download open(String owner, FileId id) throws IOException {
        Path ownerDir = ownerDir(owner);
        Path meta = resolveInside(ownerDir, id + META);
        Path blob = resolveInside(ownerDir, id + BLOB);
        if (!Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoredFileNotFoundException();
        }
        FileMetadata metadata;
        try {
            metadata = readMetadata(meta);
        } catch (IllegalArgumentException | NoSuchFileException e) {
            throw new StoredFileNotFoundException();
        }
        if (!metadata.owner().equals(owner) || !metadata.id().equals(id)) {
            throw new StoredFileNotFoundException();
        }
        return new Download(blob, metadata);
    }

    public void delete(String owner, FileId id) throws IOException {
        Download existing = open(owner, id);
        try {
            // Removing the metadata un-commits the file; the blob goes after.
            Files.delete(resolveInside(ownerDir(owner), id + META));
        } catch (NoSuchFileException e) {
            throw new StoredFileNotFoundException();
        }
        Files.deleteIfExists(existing.path());
    }

    private FileMetadata readMetadata(Path meta) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return FileMetadata.fromProperties(props);
    }

    private Path ownerDir(String owner) {
        if (owner == null || !MiniCloudProperties.USERNAME.matcher(owner).matches()) {
            throw new IllegalArgumentException("Invalid owner");
        }
        return resolveInside(files, owner);
    }

    /**
     * Resolves a single path segment and guarantees the result is a direct child of {@code dir} under the root.
     */
    static Path resolveInside(Path dir, String name) {
        Path resolved = dir.resolve(name).normalize();
        if (!dir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Path escapes its directory");
        }
        return resolved;
    }

    private void removeUncommitted() throws IOException {
        try (DirectoryStream<Path> owners = Files.newDirectoryStream(files)) {
            for (Path ownerDir : owners) {
                if (!Files.isDirectory(ownerDir, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(ownerDir)) {
                    for (Path entry : entries) {
                        String name = entry.getFileName().toString();
                        boolean orphanBlob = name.endsWith(BLOB)
                                && !Files.exists(ownerDir.resolve(name.substring(0, name.length() - BLOB.length()) + META));
                        if (orphanBlob || name.endsWith(META + TMP)) {
                            Files.deleteIfExists(entry);
                        }
                    }
                }
            }
        }
    }

    private static void deleteChildren(Path dir) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    private static Path createPrivateDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        if (Files.isSymbolicLink(dir)) {
            throw new IOException("Storage directory must not be a symbolic link");
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (posix != null) {
            try {
                posix.setPermissions(PosixFilePermissions.fromString("rwx------"));
            } catch (IOException | UncheckedIOException e) {
                log.warn("Could not restrict storage directory permissions");
            }
        }
        return dir;
    }
}
