package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.minicloud.files.UploadRejectedException.Reason;
import dev.minicloud.scan.MalwareScanner;
import dev.minicloud.scan.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileServiceTest {

    private static final long MAX = 16;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    private FileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new FileStorage(root);
    }

    @Test
    void shouldStoreAndComputeSha256() throws IOException {
        FileMetadata meta = service(file -> ScanResult.NOT_SCANNED).upload("alice", "abc.txt", 3, stream("abc"));

        assertThat(meta.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(meta.size()).isEqualTo(3);
        assertThat(meta.owner()).isEqualTo("alice");
        assertThat(meta.scanStatus()).isEqualTo(ScanResult.NOT_SCANNED);
        assertThat(storage.list("alice")).containsExactly(meta);
        assertQuarantineEmpty();
    }

    @Test
    void shouldRejectOversizedUploadEvenIfDeclaredSizeLies() {
        assertRejected(service(clean()), "big.txt", 1, "x".repeat((int) MAX + 1), Reason.TOO_LARGE);
        assertRejected(service(clean()), "big.txt", MAX + 1, "x", Reason.TOO_LARGE);
    }

    @Test
    void shouldRejectEmptyFile() {
        assertRejected(service(clean()), "empty.txt", 0, "", Reason.EMPTY);
    }

    @Test
    void shouldRejectInvalidName() {
        assertRejected(service(clean()), "../secret.txt", 3, "abc", Reason.INVALID_NAME);
    }

    @Test
    void shouldRejectSignatureMismatch() {
        assertRejected(service(clean()), "fake.png", 3, "abc", Reason.TYPE_MISMATCH);
    }

    @Test
    void shouldRejectInfectedFile() {
        assertRejected(service(file -> ScanResult.INFECTED), "a.txt", 3, "abc", Reason.INFECTED);
    }

    @Test
    void shouldRejectWhenScannerThrows() {
        MalwareScanner failing = file -> {
            throw new IOException("scanner down");
        };
        assertRejected(service(failing), "a.txt", 3, "abc", Reason.SCAN_ERROR);
    }

    @Test
    void shouldRejectWhenScannerReturnsNull() {
        assertRejected(service(file -> null), "a.txt", 3, "abc", Reason.SCAN_ERROR);
    }

    @Test
    void shouldRejectWhenInsufficientSpace() {
        FileService service = new FileService(storage, new FilenameValidator(), new FileSignatureValidator(true),
                clean(), MAX, Long.MAX_VALUE / 2, CLOCK);

        assertRejected(service, "a.txt", 3, "abc", Reason.INSUFFICIENT_SPACE);
    }

    @Test
    void shouldScanWhileFileIsInQuarantine() throws IOException {
        Path[] scanned = new Path[1];
        service(file -> {
            scanned[0] = file;
            assertThat(Files.readString(file)).isEqualTo("abc");
            return ScanResult.CLEAN;
        }).upload("alice", "a.txt", 3, stream("abc"));

        assertThat(scanned[0].getParent()).isEqualTo(storage.root().resolve("quarantine"));
    }

    @Test
    void shouldReturnNotFoundForOtherOwner() throws IOException {
        FileService service = service(clean());
        FileMetadata meta = service.upload("alice", "a.txt", 3, stream("abc"));

        assertThatThrownBy(() -> service.open("bob", meta.id().value())).isInstanceOf(StoredFileNotFoundException.class);
        assertThatThrownBy(() -> service.delete("bob", meta.id().value())).isInstanceOf(StoredFileNotFoundException.class);
        assertThat(service.open("alice", meta.id().value()).metadata()).isEqualTo(meta);
    }

    @Test
    void shouldReturnNotFoundForTraversalId() {
        assertThatThrownBy(() -> service(clean()).open("alice", "../../etc/passwd"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void shouldDeleteFile() throws IOException {
        FileService service = service(clean());
        FileMetadata meta = service.upload("alice", "a.txt", 3, stream("abc"));

        service.delete("alice", meta.id().value());

        assertThat(service.list("alice")).isEmpty();
    }

    @Test
    void shouldNotExposeFilesystemPathInErrors() {
        Throwable error = org.assertj.core.api.Assertions.catchThrowable(
                () -> service(clean()).open("alice", FileId.generate().value()));

        assertThat(error.getMessage()).doesNotContain(root.toString());
    }

    private void assertRejected(FileService service, String name, long declaredSize, String content, Reason reason) {
        assertThatThrownBy(() -> service.upload("alice", name, declaredSize, stream(content)))
                .isInstanceOfSatisfying(UploadRejectedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
        try {
            assertThat(storage.list("alice")).isEmpty();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertQuarantineEmpty();
    }

    private void assertQuarantineEmpty() {
        try (var entries = Files.list(storage.root().resolve("quarantine"))) {
            assertThat(entries).isEmpty();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private FileService service(MalwareScanner scanner) {
        return new FileService(storage, new FilenameValidator(), new FileSignatureValidator(true), scanner, MAX, 0, CLOCK);
    }

    private static MalwareScanner clean() {
        return file -> ScanResult.CLEAN;
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
