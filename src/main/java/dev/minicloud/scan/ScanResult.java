package dev.minicloud.scan;

/**
 * {@code NOT_SCANNED} means no scanner ran. It must never be presented as "clean",
 * and even {@code CLEAN} does not make a file 100% safe.
 */
public enum ScanResult {
    CLEAN,
    INFECTED,
    NOT_SCANNED
}
