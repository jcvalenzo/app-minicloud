package dev.minicloud.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;

import dev.minicloud.files.UploadRejectedException.Reason;
import dev.minicloud.scan.MalwareScanner;
import dev.minicloud.scan.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload flow: name validation -> quarantine (+SHA-256, size limit) -> signature -> scan -> atomic commit.
 */
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FileStorage storage;
    private final FilenameValidator filenameValidator;
    private final FileSignatureValidator signatureValidator;
    private final MalwareScanner scanner;
    private final long maxFileBytes;
    private final long minFreeBytes;
    private final Clock clock;

    public FileService(FileStorage storage, FilenameValidator filenameValidator,
                       FileSignatureValidator signatureValidator, MalwareScanner scanner,
                       long maxFileBytes, long minFreeBytes, Clock clock) {
        this.storage = storage;
        this.filenameValidator = filenameValidator;
        this.signatureValidator = signatureValidator;
        this.scanner = scanner;
        this.maxFileBytes = maxFileBytes;
        this.minFreeBytes = minFreeBytes;
        this.clock = clock;
    }

    /**
     * @param declaredSize client/container-reported size, used only for the free-space precheck;
     *                     the real size is counted while copying
     */
    public FileMetadata upload(String owner, String submittedName, long declaredSize, InputStream content)
            throws IOException {
        String name = filenameValidator.validate(submittedName);
        if (declaredSize > maxFileBytes) {
            throw new UploadRejectedException(Reason.TOO_LARGE);
        }
        if (storage.usableSpace() < minFreeBytes + Math.max(declaredSize, 0)) {
            throw new UploadRejectedException(Reason.INSUFFICIENT_SPACE);
        }

        Path quarantined = storage.newQuarantinePath();
        try {
            MessageDigest digest = sha256();
            long size = copyLimited(new DigestInputStream(content, digest), quarantined);
            if (size == 0) {
                throw new UploadRejectedException(Reason.EMPTY);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            String detectedType = signatureValidator.validate(quarantined, name);
            ScanResult scanResult = scan(quarantined, sha256);

            FileMetadata metadata = new FileMetadata(FileId.generate(), owner, name, size,
                    sha256, detectedType, clock.instant(), scanResult);
            storage.commit(quarantined, metadata);
            log.info("Stored file id={} owner={} size={} sha256={}", metadata.id(), owner, size, metadata.sha256());
            return metadata;
        } finally {
            Files.deleteIfExists(quarantined);
        }
    }

    public List<FileMetadata> list(String owner) throws IOException {
        return storage.list(owner);
    }

    public Download open(String owner, String rawId) throws IOException {
        return storage.open(owner, FileId.parse(rawId));
    }

    public void delete(String owner, String rawId) throws IOException {
        FileId id = FileId.parse(rawId);
        storage.delete(owner, id);
        log.info("Deleted file id={} owner={}", id, owner);
    }

    private long copyLimited(InputStream in, Path target) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxFileBytes) {
                    throw new UploadRejectedException(Reason.TOO_LARGE);
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private ScanResult scan(Path file, String sha256) {
        ScanResult result;
        try {
            result = scanner.scan(file, sha256);
        } catch (Exception e) {
            log.warn("Malware scan failed; rejecting upload");
            throw new UploadRejectedException(Reason.SCAN_ERROR, e);
        }
        if (result == null) {
            throw new UploadRejectedException(Reason.SCAN_ERROR);
        }
        if (result == ScanResult.INFECTED) {
            log.warn("Upload rejected by malware scanner sha256={}", sha256);
            throw new UploadRejectedException(Reason.INFECTED);
        }
        return result;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
