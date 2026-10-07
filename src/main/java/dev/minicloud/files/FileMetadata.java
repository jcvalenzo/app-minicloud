package dev.minicloud.files;

import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

import dev.minicloud.scan.ScanResult;

public record FileMetadata(
        FileId id,
        String owner,
        String originalName,
        long size,
        String sha256,
        String detectedType,
        Instant uploadedAt,
        ScanResult scanStatus) {

    private static final String VERSION = "1";

    public FileMetadata {
        Objects.requireNonNull(id);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(originalName);
        Objects.requireNonNull(sha256);
        Objects.requireNonNull(detectedType);
        Objects.requireNonNull(uploadedAt);
        Objects.requireNonNull(scanStatus);
    }

    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("version", VERSION);
        props.setProperty("id", id.value());
        props.setProperty("owner", owner);
        props.setProperty("originalName", originalName);
        props.setProperty("size", Long.toString(size));
        props.setProperty("sha256", sha256);
        props.setProperty("detectedType", detectedType);
        props.setProperty("uploadedAt", uploadedAt.toString());
        props.setProperty("scanStatus", scanStatus.name());
        return props;
    }

    /**
     * @throws IllegalArgumentException if the properties are incomplete or malformed
     */
    public static FileMetadata fromProperties(Properties props) {
        if (!VERSION.equals(props.getProperty("version"))) {
            throw new IllegalArgumentException("Unsupported metadata version");
        }
        try {
            return new FileMetadata(
                    FileId.parse(props.getProperty("id")),
                    props.getProperty("owner"),
                    props.getProperty("originalName"),
                    Long.parseLong(props.getProperty("size")),
                    props.getProperty("sha256"),
                    props.getProperty("detectedType"),
                    Instant.parse(props.getProperty("uploadedAt")),
                    ScanResult.valueOf(props.getProperty("scanStatus")));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed metadata", e);
        }
    }
}
