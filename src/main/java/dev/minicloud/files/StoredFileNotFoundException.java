package dev.minicloud.files;

/**
 * Thrown for missing files, malformed IDs and files owned by someone else alike,
 * so callers cannot tell them apart.
 */
public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException() {
        super("File not found");
    }
}
