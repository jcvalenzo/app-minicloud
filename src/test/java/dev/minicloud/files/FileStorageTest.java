package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import dev.minicloud.scan.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageTest {

    @TempDir
    Path root;

    private FileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new FileStorage(root);
    }

    @Test
    void shouldCommitIntoOwnerDirectoryUnderRoot() throws IOException {
        FileMetadata meta = store("alice", "hola");

        Download download = storage.open("alice", meta.id());

        assertThat(download.path()).startsWith(storage.root().resolve("files").resolve("alice"));
        assertThat(Files.readString(download.path())).isEqualTo("hola");
        assertThat(download.metadata()).isEqualTo(meta);
    }

    @Test
    void shouldIsolateOwners() throws IOException {
        FileMetadata meta = store("alice", "secreto");

        assertThatThrownBy(() -> storage.open("bob", meta.id())).isInstanceOf(StoredFileNotFoundException.class);
        assertThatThrownBy(() -> storage.delete("bob", meta.id())).isInstanceOf(StoredFileNotFoundException.class);
        assertThat(storage.list("bob")).isEmpty();
        assertThat(storage.open("alice", meta.id())).isNotNull();
    }

    @Test
    void shouldRejectMetadataClaimingAnotherOwner() throws IOException {
        FileMetadata meta = store("alice", "x");
        // Simulate a tampered/misplaced entry: alice's files copied into bob's directory.
        Path bobDir = Files.createDirectories(storage.root().resolve("files/bob"));
        Files.copy(storage.root().resolve("files/alice/" + meta.id() + ".bin"), bobDir.resolve(meta.id() + ".bin"));
        Files.copy(storage.root().resolve("files/alice/" + meta.id() + ".properties"), bobDir.resolve(meta.id() + ".properties"));

        assertThatThrownBy(() -> storage.open("bob", meta.id())).isInstanceOf(StoredFileNotFoundException.class);
        assertThat(storage.list("bob")).isEmpty();
    }

    @Test
    void shouldThrowNotFoundForMissingFile() {
        assertThatThrownBy(() -> storage.open("alice", FileId.generate())).isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void shouldRejectSymlinkedBlob() throws IOException {
        FileMetadata meta = store("alice", "x");
        Path blob = storage.root().resolve("files/alice/" + meta.id() + ".bin");
        Path outside = Files.writeString(Files.createTempFile("outside", ".txt"), "outside");
        Files.delete(blob);
        Files.createSymbolicLink(blob, outside);

        assertThatThrownBy(() -> storage.open("alice", meta.id())).isInstanceOf(StoredFileNotFoundException.class);
        Files.delete(outside);
    }

    @Test
    void shouldNotListUncommittedBlob() throws IOException {
        Path ownerDir = Files.createDirectories(storage.root().resolve("files/alice"));
        Files.writeString(ownerDir.resolve(FileId.generate() + ".bin"), "orphan");

        assertThat(storage.list("alice")).isEmpty();
    }

    @Test
    void shouldSkipCorruptMetadataWhenListing() throws IOException {
        FileMetadata good = store("alice", "ok");
        Files.writeString(storage.root().resolve("files/alice/" + FileId.generate() + ".properties"), "garbage=1");

        assertThat(storage.list("alice")).containsExactly(good);
    }

    @Test
    void shouldDeleteMetadataAndBlob() throws IOException {
        FileMetadata meta = store("alice", "x");

        storage.delete("alice", meta.id());

        assertThat(storage.list("alice")).isEmpty();
        try (var entries = Files.list(storage.root().resolve("files/alice"))) {
            assertThat(entries).isEmpty();
        }
        assertThatThrownBy(() -> storage.delete("alice", meta.id())).isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void shouldCleanQuarantineAndOrphansOnStartup() throws IOException {
        FileMetadata kept = store("alice", "keep");
        Path part = Files.writeString(storage.newQuarantinePath(), "partial");
        Path ownerDir = storage.root().resolve("files/alice");
        Path orphan = Files.writeString(ownerDir.resolve(FileId.generate() + ".bin"), "orphan");
        Path tmpMeta = Files.writeString(ownerDir.resolve(FileId.generate() + ".properties.tmp"), "tmp");

        new FileStorage(root);

        assertThat(part).doesNotExist();
        assertThat(orphan).doesNotExist();
        assertThat(tmpMeta).doesNotExist();
        assertThat(storage.open("alice", kept.id())).isNotNull();
    }

    @Test
    void shouldRoundTripUnicodeAndNewlinesInMetadata() throws IOException {
        FileMetadata meta = store("alice", "x", "ñandú = año:2024 #1.txt");

        assertThat(storage.open("alice", meta.id()).metadata().originalName()).isEqualTo("ñandú = año:2024 #1.txt");
    }

    @Test
    void shouldKeepResolvedPathsInsideDirectory() {
        Path dir = storage.root().resolve("files");
        assertThatIllegalArgumentException().isThrownBy(() -> FileStorage.resolveInside(dir, "../secret.txt"));
        assertThatIllegalArgumentException().isThrownBy(() -> FileStorage.resolveInside(dir, "../../secret.txt"));
        assertThatIllegalArgumentException().isThrownBy(() -> FileStorage.resolveInside(dir, "/etc/passwd"));
        assertThatIllegalArgumentException().isThrownBy(() -> FileStorage.resolveInside(dir, "a/b"));
        assertThatIllegalArgumentException().isThrownBy(() -> FileStorage.resolveInside(dir, "."));
        assertThat(FileStorage.resolveInside(dir, "x.bin")).isEqualTo(dir.resolve("x.bin"));
    }

    @Test
    void shouldRejectInvalidOwner() {
        assertThatIllegalArgumentException().isThrownBy(() -> storage.list("../alice"));
    }

    private FileMetadata store(String owner, String content) throws IOException {
        return store(owner, content, "file.txt");
    }

    private FileMetadata store(String owner, String content, String name) throws IOException {
        Path part = Files.writeString(storage.newQuarantinePath(), content);
        FileMetadata meta = new FileMetadata(FileId.generate(), owner, name, content.length(), "00",
                "txt", Instant.parse("2026-01-01T00:00:00Z"), ScanResult.NOT_SCANNED);
        storage.commit(part, meta);
        return meta;
    }
}
